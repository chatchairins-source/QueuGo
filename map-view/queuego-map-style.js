/* Shortbread vector schema; independent from the routing graph version. */
(function(root){
  "use strict";
  function create(url){
    if(!url)return {version:8,sources:{osm:{type:"raster",tiles:["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],tileSize:256,attribution:"© OpenStreetMap contributors"}},layers:[{id:"background",type:"background",paint:{"background-color":"#eeeae1"}},{id:"osm",type:"raster",source:"osm"}]};
    const layers=[{id:"background",type:"background",paint:{"background-color":"#f3f0e8"}}];
    const add=(id,sourceLayer,type,paint,extra={})=>layers.push({id,type,source:"buriram","source-layer":sourceLayer,paint,...extra});
    add("land","land","fill",{"fill-color":"#dfe8cf","fill-opacity":.65});
    add("water","water_polygons","fill",{"fill-color":"#b7dae7"});
    add("water-lines","water_lines","line",{"line-color":"#b7dae7","line-width":2});
    add("buildings","buildings","fill",{"fill-color":"#ded7cd"},{minzoom:14});
    const width=["interpolate",["linear"],["zoom"],9,1,13,3,17,10];
    add("road-case","streets","line",{"line-color":"#c8bfb1","line-width":["interpolate",["linear"],["zoom"],9,2,13,5,17,13]},{layout:{"line-cap":"round","line-join":"round"}});
    add("roads","streets","line",{"line-color":["match",["get","kind"],["motorway","trunk"],"#f4bd77",["primary","secondary"],"#ffe0a0","#ffffff"],"line-width":width},{layout:{"line-cap":"round","line-join":"round"}});
    add("street-names","street_labels","symbol",{"text-color":"#514c46","text-halo-color":"#fff","text-halo-width":1.5},{minzoom:13,layout:{"symbol-placement":"line","text-field":["coalesce",["get","name"],["get","ref"],""],"text-font":["Noto Sans Regular"],"text-size":12}});
    add("place-names","place_labels","symbol",{"text-color":"#403c37","text-halo-color":"#fff","text-halo-width":2},{layout:{"text-field":["coalesce",["get","name"],["get","name_en"],""],"text-font":["Noto Sans Regular"],"text-size":14}});
    return {version:8,glyphs:location.origin+'/map-assets/fonts/{fontstack}/{range}.pbf',sources:{buriram:{type:"vector",url:"pmtiles://"+url,attribution:"© OpenStreetMap contributors · Shortbread"}},layers};
  }
  root.QueueGoMapStyle=Object.freeze({create});
})(globalThis);
