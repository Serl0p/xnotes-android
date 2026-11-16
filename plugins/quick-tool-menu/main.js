// API 8: native visual settings and host-rendered palette wheel.
// Script sees no note data, and cannot set color without the user's explicit native tap.
const labels = {pen:'Pen',dashed:'Dashed pen',calligraphy:'Calligraphy',speed:'Speed pen',
  taper:'Taper pen',highlighter:'Highlighter',eraser:'Eraser',pan:'Pan',select:'Select',
  lasso:'Lasso',shape:'Shape',markup:'PDF text markup'};
const cfg = context.settings;
const center = ['palette','swatches','close'].includes(cfg.center) ? cfg.center : 'palette';
const size = ['compact','regular','large'].includes(cfg.size) ? cfg.size : 'regular';
const allowed = new Set(context.tools);
const valid = id => allowed.has(id) && Object.prototype.hasOwnProperty.call(labels,id);
let favorites = [...new Set(String(cfg.favorites || '').split(',').map(s=>s.trim()))].filter(valid);
if (cfg['active-first'] === 'true' && valid(context.activeTool) && favorites.includes(context.activeTool))
  favorites = [context.activeTool,...favorites.filter(t=>t!==context.activeTool)];
if (!favorites.length) favorites = [context.activeTool,...context.tools].filter(valid).slice(0,1);
const limit = Math.min(center==='close'?8:10,Math.max(3,Number.parseInt(cfg.limit,10)||8));
const colors = [...new Set(String(cfg.colors || '').split(',').map(c=>c.trim().toLowerCase()))]
  .filter(c=>/^#[0-9a-f]{6}$/.test(c)).slice(0,6);
return {layout:'radial',center,size,title:context.surface==='infinite'?'Quick tools — canvas':'Quick tools — note',
  ...(center==='close'?{}:{colors}),items:favorites.slice(0,limit).map(tool=>({label:labels[tool],tool}))};
