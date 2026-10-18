export const STACK_FINANCIAL_POLICY_VERSION = 'stack-savings-group-weighted-v1';

function nonNegativeSafeInteger(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) throw new Error(\`invalid \${label}\`);
  return value;
}

function parsePercentToBasisPoints(value) {
  const text = String(value).trim();
  if (!/^\\d+(?:\\.\\d{1,2})?$/.test(text)) throw new Error('invalid existing group savings percent');
  const [whole, fraction = ''] = text.split('.');
  const basisPoints = Number(whole) * 100 + Number((fraction + '00').slice(0, 2));
  if (!Number.isSafeInteger(basisPoints) || basisPoints <= 0 || basisPoints >= 10000) {
    throw new Error('invalid existing group savings percent');
  }
  return basisPoints;
}

function roundHalfUpRatio(numerator, denominator) {
  const n = BigInt(numerator);
  const d = BigInt(denominator);
  return Number((n + d / 2n) / d);
}

function reject(reason, extra = {}) {
  return { policyVersion: STACK_FINANCIAL_POLICY_VERSION, eligible: false, reason, ...extra };
}

function weightedSavings(existingOrders, totalSavingSatang) {
  const totalFee = existingOrders.reduce((sum, order) => sum + order.currentFeeSatang, 0);
  if (totalFee <= 0) return null;
  const denominator = BigInt(totalFee);
  const rows = existingOrders.map(order => {
    const numerator = BigInt(totalSavingSatang) * BigInt(order.currentFeeSatang);
    return {
      order,
      savingSatang: Number(numerator / denominator),
      remainder: numerator % denominator,
    };
  });
  let residual = totalSavingSatang - rows.reduce((sum, row) => sum + row.savingSatang, 0);
  const ranked = [...rows].sort((a, b) => {
    if (a.remainder !== b.remainder) return a.remainder > b.remainder ? -1 : 1;
    return a.order.orderId.localeCompare(b.order.orderId);
  });
  for (let i = 0; i < residual; i += 1) ranked[i].savingSatang += 1;
  return rows;
}

export function simulateStackSavingsGroupWeightedV1(input) {
  if (!input || typeof input !== 'object') throw new Error('invalid financial simulation input');
  if (!Array.isArray(input.existingOrders) || input.existingOrders.length < 1 || input.existingOrders.length > 4) {
    throw new Error('invalid existing stack orders');
  }
  if (!input.candidate || typeof input.candidate !== 'object') throw new Error('invalid candidate order');

  const candidateId = String(input.candidate.orderId || '');
  if (!candidateId) throw new Error('invalid candidate order id');
  const seen = new Set([candidateId]);
  const existingOrders = input.existingOrders.map(order => {
    const orderId = String(order?.orderId || '');
    if (!orderId || seen.has(orderId)) throw new Error('duplicate or invalid order id');
    seen.add(orderId);
    return {
      orderId,
      currentFeeSatang: nonNegativeSafeInteger(order.currentFeeSatang, 'current fee'),
      lockedRiderPayoutSatang: nonNegativeSafeInteger(order.lockedRiderPayoutSatang, 'locked rider payout'),
    };
  });
  const candidate = {
    orderId: candidateId,
    currentFeeSatang: nonNegativeSafeInteger(input.candidate.currentFeeSatang, 'candidate current fee'),
  };
  const incrementalCompensationSatang = nonNegativeSafeInteger(
    input.incrementalCompensationSatang,
    'incremental compensation'
  );
  const splitBasisPoints = parsePercentToBasisPoints(input.existingGroupSavingsPercent);

  const availableSavingSatang = candidate.currentFeeSatang - incrementalCompensationSatang;
  if (availableSavingSatang <= 0) {
    return reject('candidate has no positive saving', {
      availableSavingSatang: Math.max(availableSavingSatang, 0),
      fundingGapSatang: 0,
    });
  }

  const existingFeeTotalSatang = existingOrders.reduce((sum, order) => sum + order.currentFeeSatang, 0);
  const rawGroupSavingSatang = roundHalfUpRatio(
    BigInt(availableSavingSatang) * BigInt(splitBasisPoints),
    10000n
  );
  const existingGroupSavingSatang = Math.min(rawGroupSavingSatang, existingFeeTotalSatang);
  const candidateSavingSatang = availableSavingSatang - existingGroupSavingSatang;
  if (existingGroupSavingSatang <= 0 || candidateSavingSatang <= 0) {
    return reject('both existing group and candidate require positive saving', {
      availableSavingSatang,
      existingGroupSavingSatang,
      candidateSavingSatang,
      fundingGapSatang: 0,
    });
  }

  const weighted = weightedSavings(existingOrders, existingGroupSavingSatang);
  if (!weighted || weighted.some(row => row.savingSatang <= 0)) {
    return reject('every existing beneficiary requires positive saving', {
      availableSavingSatang,
      existingGroupSavingSatang,
      candidateSavingSatang,
      fundingGapSatang: 0,
    });
  }

  const allocations = weighted.map(row => ({
    orderId: row.order.orderId,
    role: 'existing',
    currentFeeSatang: row.order.currentFeeSatang,
    customerSavingSatang: row.savingSatang,
    proposedFeeSatang: row.order.currentFeeSatang - row.savingSatang,
    riderPayoutSatang: row.order.lockedRiderPayoutSatang,
  }));
  allocations.push({
    orderId: candidate.orderId,
    role: 'candidate',
    currentFeeSatang: candidate.currentFeeSatang,
    customerSavingSatang: candidateSavingSatang,
    proposedFeeSatang: candidate.currentFeeSatang - candidateSavingSatang,
    riderPayoutSatang: incrementalCompensationSatang,
  });

  if (allocations.some(row => row.proposedFeeSatang < 0 || row.proposedFeeSatang > row.currentFeeSatang)) {
    return reject('customer fee ceiling violated');
  }

  const proposedCollectionsSatang = allocations.reduce((sum, row) => sum + row.proposedFeeSatang, 0);
  const riderPayoutTotalSatang = allocations.reduce((sum, row) => sum + row.riderPayoutSatang, 0);
  const fundingDeltaSatang = riderPayoutTotalSatang - proposedCollectionsSatang;
  const fundingGapSatang = Math.max(fundingDeltaSatang, 0);
  const platformMarginSatang = Math.max(-fundingDeltaSatang, 0);
  const totalCustomerSavingSatang = allocations.reduce((sum, row) => sum + row.customerSavingSatang, 0);

  const result = {
    policyVersion: STACK_FINANCIAL_POLICY_VERSION,
    eligible: fundingGapSatang === 0,
    reason: fundingGapSatang === 0 ? null : 'preserved rider payouts are not fully funded',
    existingGroupSavingsBasisPoints: splitBasisPoints,
    availableSavingSatang,
    existingGroupSavingSatang,
    candidateSavingSatang,
    totalCustomerSavingSatang,
    proposedCollectionsSatang,
    riderPayoutTotalSatang,
    fundingDeltaSatang,
    fundingGapSatang,
    platformMarginSatang,
    allocations,
  };

  if (totalCustomerSavingSatang !== availableSavingSatang) throw new Error('saving conservation failed');
  if (allocations.slice(0, -1).some((row, i) => row.riderPayoutSatang !== existingOrders[i].lockedRiderPayoutSatang)) {
    throw new Error('locked rider payout changed');
  }
  if (allocations.at(-1).riderPayoutSatang !== incrementalCompensationSatang) {
    throw new Error('candidate payout changed');
  }
  if (proposedCollectionsSatang + totalCustomerSavingSatang !== existingFeeTotalSatang + candidate.currentFeeSatang) {
    throw new Error('collection conservation failed');
  }
  return result;
}
