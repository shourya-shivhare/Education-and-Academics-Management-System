import { useEffect, useMemo, useState } from 'react';
import { schemaApi } from '../api/entities';
import { PageShell, ErrorAlert, InlineTrace, Input } from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

function DiagramFallback({ tables }) {
  return <div className="dash-card" style={{ background: 'linear-gradient(135deg, #eef2ff, #f8fafc)' }}>
    <h3>Relational model overview</h3>
    <p className="form-hint">The live explorer below is generated from MySQL information_schema. The ER image is optional; this overview remains available when the image file is not mounted into Docker.</p>
    <div className="form-grid">
      {[
        ['Person', 'Student · Faculty · Person_Phone'],
        ['Academic', 'Department · Program · Subject · Term'],
        ['Delivery', 'Course_Offering · Timetable_Slot · Classroom'],
        ['Progress', 'Enrollment · Attendance · Assessment · Marks'],
        ['Governance', 'Users · Audit_Log · Fee_Payment · Student_Risk'],
      ].map(([title, detail]) => <div key={title} className="dash-card"><strong>{title}</strong><p>{detail}</p></div>)}
    </div>
    <small>{tables.length} live tables discovered</small>
  </div>;
}

export default function SchemaPage() {
  const [schema, setSchema] = useState({ database: 'edu_db', tables: [], columns: [], foreignKeys: [], indexes: [] });
  const [table, setTable] = useState('');
  const [image, setImage] = useState('');
  const [imageFailed, setImageFailed] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [trace, setTrace] = useState([]);

  const load = async name => {
    setLoading(true);
    setError(null);
    try {
      const data = await schemaApi.get(name);
      setSchema(data || {});
      setTrace(getLatestTrace());
    } catch (e) {
      setError(e?.response?.data?.message || e?.response?.data?.error?.message || e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load('');
    schemaApi.erImage()
      .then(blob => setImage(URL.createObjectURL(blob)))
      .catch(() => setImageFailed(true));
  }, []);

  const tables = schema.tables || [];
  const selectedTable = useMemo(() => table || 'all tables', [table]);
  return <PageShell title="Schema / ER Explorer" subtitle={`Live MySQL metadata · database: ${schema.database || 'edu_db'}`}>
    <ErrorAlert message={error} onClose={() => setError(null)} />
    {image && !imageFailed ? <section className="dash-card"><h3>Entity relationship diagram</h3><img src={image} alt="Entity relationship diagram" onError={() => setImageFailed(true)} style={{ width: '100%', maxHeight: 560, objectFit: 'contain' }} /></section> : <DiagramFallback tables={tables} />}
    <section className="dash-card">
      <div className="entity-header"><div><h3>Live schema explorer</h3><p className="form-hint">Inspect all tables or select one table to narrow columns, keys, foreign keys, and indexes.</p></div>
        <div className="entity-actions"><Input value={table} onChange={setTable} placeholder="Table name" /><button className="btn btn-primary" onClick={() => load(table.trim())} disabled={loading}>Inspect</button><button className="btn btn-outline" onClick={() => { setTable(''); load(''); }}>All tables</button></div>
      </div>
      <p className="form-hint">Showing: <strong>{selectedTable}</strong></p>
      {loading ? <div className="table-loading"><div className="spinner" /><span>Reading information_schema…</span></div> : <>
        <h3>Tables ({tables.length})</h3><div className="table-wrapper"><table className="entity-table"><thead><tr><th>Table</th><th>Type</th><th>Engine</th><th>Estimated rows</th></tr></thead><tbody>
          {tables.map(row => <tr key={row.TABLE_NAME} onClick={() => { setTable(row.TABLE_NAME); load(row.TABLE_NAME); }} style={{ cursor: 'pointer' }}><td>{row.TABLE_NAME}</td><td>{row.TABLE_TYPE}</td><td>{row.ENGINE || '—'}</td><td>{String(row.TABLE_ROWS ?? '—')}</td></tr>)}
        </tbody></table></div>
        <h3>Columns and keys ({(schema.columns || []).length})</h3><div className="table-wrapper"><table className="entity-table"><thead><tr><th>Table</th><th>Column</th><th>Type</th><th>Nullable</th><th>Key</th><th>Default</th><th>Extra</th></tr></thead><tbody>
          {(schema.columns || []).map((row, i) => <tr key={`${row.TABLE_NAME}-${row.COLUMN_NAME}-${i}`}><td>{row.TABLE_NAME}</td><td>{row.COLUMN_NAME}</td><td>{row.COLUMN_TYPE}</td><td>{row.IS_NULLABLE}</td><td>{row.COLUMN_KEY || '—'}</td><td>{String(row.COLUMN_DEFAULT ?? '—')}</td><td>{row.EXTRA || '—'}</td></tr>)}
        </tbody></table></div>
        <h3>Foreign keys ({(schema.foreignKeys || []).length})</h3><div className="table-wrapper"><table className="entity-table"><thead><tr><th>Child</th><th>Column</th><th>Parent</th><th>Column</th><th>Update</th><th>Delete</th></tr></thead><tbody>
          {(schema.foreignKeys || []).map((row, i) => <tr key={`${row.CONSTRAINT_NAME}-${i}`}><td>{row.child_table}</td><td>{row.child_column}</td><td>{row.parent_table}</td><td>{row.parent_column}</td><td>{row.UPDATE_RULE}</td><td>{row.DELETE_RULE}</td></tr>)}
        </tbody></table></div>
        <h3>Indexes ({(schema.indexes || []).length})</h3><div className="table-wrapper"><table className="entity-table"><thead><tr><th>Table</th><th>Index</th><th>Column</th><th>Unique</th><th>Type</th></tr></thead><tbody>
          {(schema.indexes || []).map((row, i) => <tr key={`${row.TABLE_NAME}-${row.INDEX_NAME}-${i}`}><td>{row.TABLE_NAME}</td><td>{row.INDEX_NAME}</td><td>{row.COLUMN_NAME}</td><td>{row.NON_UNIQUE === 0 ? 'Yes' : 'No'}</td><td>{row.INDEX_TYPE}</td></tr>)}
        </tbody></table></div>
      </>}
    </section>
    {trace.length > 0 && <InlineTrace trace={trace} />}
  </PageShell>;
}
