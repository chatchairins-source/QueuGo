export function nativeRegistrationCandidate(currentUserId,deviceId,platform,deviceRow,tokenRow){
  if(!currentUserId||!deviceId)return {error:'registration unavailable',status:403};
  if(deviceRow&&tokenRow&&deviceRow.id!==tokenRow.id){
    return {error:'registration conflict',status:409};
  }
  const row=deviceRow||tokenRow||null;
  if(!row)return {mode:'create',id:deviceId};
  if(row.id!==deviceId)return {error:'token unavailable',status:403};
  if(row.platform&&row.platform!==platform)return {error:'device unavailable',status:403};
  if(row.user_id===currentUserId)return {mode:'owned',id:row.id};
  return {
    mode:'foreign',
    id:row.id,
    previousUserId:row.user_id,
    expectedSessionId:row.session_id||null,
    expectedToken:row.token||null
  };
}

export function finalizeNativeRegistration(candidate,previousBindingActive){
  if(!candidate||candidate.error||candidate.mode!=='foreign')return candidate;
  if(previousBindingActive!==false){
    return {error:'device unavailable',status:403};
  }
  return {
    mode:'reclaim',
    id:candidate.id,
    expectedOwner:candidate.previousUserId,
    expectedSessionId:candidate.expectedSessionId||null,
    expectedToken:candidate.expectedToken||null
  };
}
