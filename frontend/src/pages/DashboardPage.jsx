import { useState, useEffect, useCallback, useRef } from 'react';
import { FolderOpen } from 'lucide-react';
import toast from 'react-hot-toast';
import Navbar from '../components/Navbar';
import FileUpload from '../components/FileUpload';
import DocumentCard from '../components/DocumentCard';
import { listDocuments } from '../services/api';

function DashboardPage() {
  const [documents, setDocuments] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const intervalRef = useRef(null);

  const fetchDocuments = useCallback(async (pageNum = page) => {
    try {
      const { data } = await listDocuments(pageNum, 12);
      setDocuments(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (err) {
      // Only show error toast if it's not a silent refresh
      if (loading) {
        toast.error('Failed to load documents.');
      }
    } finally {
      setLoading(false);
    }
  }, [page, loading]);

  // Initial fetch and page change
  useEffect(() => {
    setLoading(true);
    fetchDocuments(page);
  }, [page]);

  // Auto-refresh when documents are processing
  useEffect(() => {
    const hasProcessing = documents.some(
      (doc) => doc.status === 'PROCESSING' || doc.status === 'UPLOADED'
    );

    if (hasProcessing) {
      intervalRef.current = setInterval(() => {
        fetchDocuments(page);
      }, 10000);
    }

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [documents, page, fetchDocuments]);

  const handleUploadComplete = useCallback(() => {
    // Refresh the list after upload
    setTimeout(() => fetchDocuments(0), 500);
    setPage(0);
  }, [fetchDocuments]);

  const handleDelete = useCallback((deletedId) => {
    setDocuments((prev) => prev.filter((doc) => doc.id !== deletedId));
    setTotalElements((prev) => Math.max(0, prev - 1));
  }, []);

  const handlePrevPage = () => setPage((p) => Math.max(0, p - 1));
  const handleNextPage = () => setPage((p) => Math.min(totalPages - 1, p + 1));

  return (
    <div className="fade-in">
      <Navbar />

      <div className="page-container">
        {/* Upload Section */}
        <FileUpload onUploadComplete={handleUploadComplete} />

        {/* Documents Section */}
        <div className="section-header">
          <h2 className="section-title">Your Documents</h2>
          {totalElements > 0 && (
            <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              {totalElements} document{totalElements !== 1 ? 's' : ''}
            </span>
          )}
        </div>

        {loading ? (
          <div className="loading-center">
            <div className="loading-spinner" />
            <p style={{ color: 'var(--text-secondary)' }}>Loading documents...</p>
          </div>
        ) : documents.length === 0 ? (
          <div className="empty-state slide-up">
            <FolderOpen size={64} className="empty-state-icon" />
            <h3 className="empty-state-title">No documents yet</h3>
            <p className="empty-state-text">
              Upload your first document to get started. We support PDF, CSV, JPG, and PNG files.
            </p>
          </div>
        ) : (
          <>
            <div className="document-grid">
              {documents.map((doc) => (
                <DocumentCard key={doc.id} document={doc} onDelete={handleDelete} />
              ))}
            </div>

            {totalPages > 1 && (
              <div className="pagination">
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={handlePrevPage}
                  disabled={page === 0}
                >
                  Previous
                </button>
                <span className="pagination-info">
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={handleNextPage}
                  disabled={page >= totalPages - 1}
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default DashboardPage;
