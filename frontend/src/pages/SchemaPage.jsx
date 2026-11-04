import { useEffect, useState } from 'react';
import { schemaApi } from '../api/entities';
import { PageShell, ErrorAlert, InlineTrace, Input } from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

export default function SchemaPage() {
  const [schema, setSchema] = useState({ tables: [], columns: [], foreignKeys: [], indexes: [] });
  const [table, setTable] = useState('');
  const [image, setImage] = useState('');
  const [error, setError] = useState(null);
  const [trace, setTrace] = useState([]);
  const load = async name => {
    try { setSchema(await schemaApi.get(name)); setTrace(getLatestTrace()); }
    catch (e) { setError(e?.response?.data?.message || e.message); }
  };
  useEffect(() => { load(''); schemaApi.erImage().then(blob => setImage(URL.createObjectURL(blob))).catch(() => {}); }, []);
  const tables = schema.tables || [];
  return <PageShell title="Schema / ER Explorer" subtitle={`Database: ${schema.database || 'edu_db'}`}>
    <ErrorAlert message={error} onClose={() => setError(null)} />
    {image && <img src={image} alt="Entity relationship diagram" style={{ maxWidth: '100%', maxHeight: 520, objectFit: 'contain' }} />}
    <div className="dash-card"><div className="entity-actions"><Input value={table} onChange={setTable} placeholder="Filter table name" /><button className="btn btn-primary" onClick={() => load(table)}>Inspect</button><button className="btn btn-outline" onClick={() => { setTable(''); load(''); }}>All tables</button></div>
      <h3>Tables</h3><div className="table-wrapper"><table className="entity-table"><thead><tr><th>Table</th><th>Type</th><th>Engine</th><th>Rows</th></tr></thead><tbody>{tables.map(r => <tr key={r.TABLE_NAME}><td>{r.TABLE_NAME}</td><td>{r.TABLE_TYPE}</td><td>{r.ENGINE}</td><td>{String(r.TABLE_ROWS ?? '')}</td></tr>)}</tbody></table></div>
      <h3>Columns</h3><div className="table-wrapper"><table className="entity-table"><thead><tr><th>Table</th><th>Column</th><th>Type</th><th>Nullable</th><th>Key</th></tr></thead><tbody>{(schema.columns || []).map((r, i) => <tr key={i}><td>{r.TABLE_NAME}</td><td>{r.COLUMN_NAME}</td><td>{r.COLUMN_TYPE}</td><td>{r.IS_NULLABLE}</td><td>{r.COLUMN_KEY}</td></tr>)}</tbody></table></div>
      <h3>Foreign keys</h3><div className="table-wrapper"><table className="entity-table"><thead><tr><th>Child</th><th>Column</th><th>Parent</th><th>Column</th><th>Delete</th></tr></thead><tbody>{(schema.foreignKeys || []).map((r, i) => <tr key={i}><td>{r.child_table}</td><td>{r.child_column}</td><td>{r.parent_table}</td><td>{r.parent_column}</td><td>{r.DELETE_RULE}</td></tr>)}</tbody></table></div>
    </div>{trace.length > 0 && <InlineTrace trace={trace} />}
  </PageShell>;
}
