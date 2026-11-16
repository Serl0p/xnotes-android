// API 7: evaluated on context/settings changes; no document data or Android objects.
const settings = context.settings;
const valid = new Set(context.items);
const pdf = context.hasPdf;
const canvas = context.surface === 'infinite';
const pens = {type:'group',label:'Pens',tools:['pen','dashed','calligraphy','speed','taper']};
const cmd = (id,label) => ({type:'command',id,label});
const width = String(settings.widths || '').split(',').map(s => Number(s.trim()))
  .filter(n => Number.isFinite(n) && n >= 0.1 && n <= 100).slice(0,5).map(value=>({type:'width',value}));
const first = ['home', 'title', ...(pdf ? ['sidebar','markup',pens,'highlighter','eraser','lasso','undo','redo','view','page_nav'] :
  [pens,'highlighter','eraser','lasso','pan','shape','undo','redo', ...(canvas ? ['waypoints','minimap'] : ['sidebar','text','view','page_nav'])])];
const second = ['colors', ...width, 'image','styles','zoom','fit','zoom_lock',cmd('quick-tool-menu:open','Quick Tool Menu'),
  ...(pdf ? [cmd('advanced-pdf-annotations:open','Annotations'),cmd('universal-exporter:export','Export')] : [])];
const clean = row => row.filter(x=>typeof x !== 'string' || valid.has(x));
const one = settings.rows === '1';
const style = {position:['top','bottom','left','right'].includes(settings.position) ? settings.position : 'top',
  size:['compact','regular','comfortable'].includes(settings.size) ? settings.size : 'compact',
  floating:settings.floating === 'true',radius:18,spacing:1};
if (/^#[0-9a-f]{6}$/i.test(settings.background || '')) style.background=settings.background;
return {style,collapsible:true,rows:one?[clean([...first,...second])]:[clean(first),clean(second)]};
