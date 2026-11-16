// Executed as a function body with a read-only-by-copy context.
// Only the returned menu description crosses back into the host.
const labels = {
  pen: 'Pen', dashed: 'Dashed pen', calligraphy: 'Calligraphy', speed: 'Speed pen',
  taper: 'Taper pen', highlighter: 'Highlighter', eraser: 'Eraser', pan: 'Pan',
  select: 'Select', lasso: 'Lasso', shape: 'Shape', markup: 'PDF text markup'
};
const favorites = String(context.settings.favorites || '').split(',').map(s => s.trim());
const ordered = [...new Set([context.activeTool, ...favorites, ...context.tools])]
  .filter(id => context.tools.includes(id) && Object.hasOwn(labels, id));
const limit = Math.min(16, Math.max(3, Number.parseInt(context.settings.limit, 10) || 7));
return {
  title: context.surface === 'infinite' ? 'Quick tools — canvas' : 'Quick tools — note',
  items: ordered.slice(0, limit).map(id => ({ label: labels[id], tool: id }))
};
