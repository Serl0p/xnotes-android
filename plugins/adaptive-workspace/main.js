// API 8: visual settings + adaptive native toolbar specification.
const cfg=context.settings;
const valid=new Set(context.items),pdf=context.hasPdf,canvas=context.surface==='infinite';
const csv=s=>[...new Set(String(s||'').split(',').map(x=>x.trim()).filter(Boolean))];
const penIds=csv(cfg.pens).filter(t=>valid.has(t));
const pens={type:'group',label:'Pens',tools:penIds.length?penIds:['pen']};
const cmd=(id,label)=>({type:'command',id,label});
const width=csv(cfg.widths).map(Number).filter(n=>Number.isFinite(n)&&n>=0.1&&n<=100).slice(0,5).map(value=>({type:'width',value}));
const primary=csv(cfg.primary).filter(t=>valid.has(t));
const first=['home','title',...(pdf?['sidebar','markup',pens,...primary,'undo','redo','view','page_nav']:
 [pens,...primary,'undo','redo',...(canvas?['waypoints','minimap']:['sidebar','text','view','page_nav'])])];
const second=['colors',...width,'image','styles','zoom','fit','zoom_lock',cmd('quick-tool-menu:open','Quick Tool Menu'),
 ...(pdf?[cmd('advanced-pdf-annotations:open','Annotations'),cmd('universal-exporter:export','Export')]:[])];
const seen=new Set();
const clean=row=>row.filter(x=>typeof x!=='string'||(valid.has(x)&&!seen.has(x)&&(seen.add(x),true)));
const style={position:['top','bottom','left','right'].includes(cfg.position)?cfg.position:'top',
 size:['compact','regular','comfortable'].includes(cfg.size)?cfg.size:'compact',
 floating:cfg.floating==='true',radius:18,spacing:1};
if(/^#[0-9a-f]{6}$/i.test(cfg.background||''))style.background=cfg.background;
const rows=cfg.rows==='1'?[clean([...first,...second])]:[clean(first),clean(second)].filter(r=>r.length);
return {style,collapsible:true,rows};
