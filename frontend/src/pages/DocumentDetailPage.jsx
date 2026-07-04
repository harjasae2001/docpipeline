import { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft,
  Download,
  FileBarChart,
  FileText,
  Clock,
  AlertTriangle,
  Loader2,
} from 'lucide-react';
import toast from 'react-hot-toast';
import Navbar from '../components/Navbar';
import StatusBadge from '../components/StatusBadge';
import { formatFileSize, formatDate } from '../utils/format';
import {
  getDocument,
  getDownloadUrl,
  generateReport,
  getReportDownloadUrl,
} from '../services/api';

function DocumentDetailPage() {
  const { id } = useParams();
  const [document, setDocument] = useState(null);
  const [loading, setLoading] = useState(true);
  const [reportLoading, setReportLoading] = useState(false);
  const [reportUrl, setReportUrl] = useState(null);
  const [error, setError] = useState(null);

  const fetchDocument = useCallback(async () => {
    try {
      const { data } = await getDocument(id);
      setDocument(data);
      setError(null);
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to load document.');
      toast.error('Failed to load document details.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchDocument();
  }, [fetchDocument]);

  // Auto-refresh while processing
  useEffect(() => {
    let interval;
    if (document && (document.status === 'PROCESSING' || document.status === 'UPLOADED')) {
      interval = setInterval(fetchDocument, 5000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [document, fetchDocument]);

  const handleDownload = async () => {
    try {
      const { data } = await getDownloadUrl(id);
      window.open(data.downloadUrl || data.url || data, '_blank');
    } catch (err) {
      toast.error('Failed to get download link.');
    }
  };

  const handleGenerateReport = async () => {
    setReportLoading(true);
    try {
      await generateReport(id);
      toast.success('Report generated successfully!');

      // Fetch report download URL
      const { data } = await getReportDownloadUrl(id);
      setReportUrl(data.downloadUrl || data.url || data);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to generate report.');
    } finally {
      setReportLoading(false);
    }
  };

  const handleDownloadReport = () => {
    if (reportUrl) {
      window.open(reportUrl, '_blank');
    }
  };

  // Parse metadata JSON safely
  const parseMetadata = (metadata) => {
    if (!metadata) return null;
    if (typeof metadata === 'object') return metadata;
    try {
      return JSON.parse(metadata);
    } catch {
      return null;
    }
  };

  if (loading) {
    return (
      <div className="fade-in">
        <Navbar />
        <div className="page-container">
          <div className="loading-center" style={{ minHeight: '60vh' }}>
            <div className="loading-spinner" />
            <p style={{ color: 'var(--text-secondary)' }}>Loading document...</p>
          </div>
        </div>
      </div>
    );
  }

  if (error || !document) {
    return (
      <div className="fade-in">
        <Navbar />
        <div className="page-container">
          <Link to="/dashboard" className="back-link" style={{ marginBottom: '2rem', display: 'inline-flex' }}>
            <ArrowLeft size={18} />
            Back to Dashboard
          </Link>
          <div className="empty-state">
            <AlertTriangle size={64} className="empty-state-icon" style={{ color: 'var(--error)' }} />
            <h3 className="empty-state-title">Document Not Found</h3>
            <p className="empty-state-text">{error || 'The document you are looking for does not exist.'}</p>
          </div>
        </div>
      </div>
    );
  }

  const metadata = parseMetadata(document.metadata);

  return (
    <div className="fade-in">
      <Navbar />
      <div className="page-container">
        {/* Back Link */}
        <div className="detail-header slide-up">
          <Link to="/dashboard" className="back-link">
            <ArrowLeft size={18} />
            Back to Dashboard
          </Link>
        </div>

        {/* Document Info */}
        <div className="glass-card detail-info slide-up">
          <div className="detail-info-item">
            <div className="detail-info-label">File Name</div>
            <div className="detail-info-value">{document.fileName}</div>
          </div>
          <div className="detail-info-item">
            <div className="detail-info-label">Content Type</div>
            <div className="detail-info-value" style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>
              {document.contentType || '—'}
            </div>
          </div>
          <div className="detail-info-item">
            <div className="detail-info-label">File Size</div>
            <div className="detail-info-value">{formatFileSize(document.fileSize)}</div>
          </div>
          <div className="detail-info-item">
            <div className="detail-info-label">Status</div>
            <div className="detail-info-value">
              <StatusBadge status={document.status} />
            </div>
          </div>
          <div className="detail-info-item">
            <div className="detail-info-label">Uploaded</div>
            <div className="detail-info-value">{formatDate(document.createdAt)}</div>
          </div>
          <div className="detail-info-item">
            <div className="detail-info-label">Last Updated</div>
            <div className="detail-info-value">{formatDate(document.updatedAt)}</div>
          </div>
        </div>

        {/* Processing State */}
        {(document.status === 'PROCESSING' || document.status === 'UPLOADED') && (
          <div className="loading-center slide-up" style={{ padding: '3rem' }}>
            <Loader2 size={48} style={{ color: 'var(--accent-purple)', animation: 'spin 1s linear infinite' }} />
            <p style={{ color: 'var(--text-secondary)', fontSize: '1.1rem' }}>
              Processing your document...
            </p>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              This page will update automatically when processing is complete.
            </p>
          </div>
        )}

        {/* Failed State */}
        {document.status === 'FAILED' && (
          <div className="glass-card slide-up" style={{ padding: '2rem', textAlign: 'center' }}>
            <AlertTriangle size={48} style={{ color: 'var(--error)', marginBottom: '1rem' }} />
            <h3 style={{ color: 'var(--error)', marginBottom: '0.5rem' }}>Processing Failed</h3>
            <p style={{ color: 'var(--text-secondary)' }}>
              {document.errorMessage || 'An error occurred while processing this document. Please try uploading it again.'}
            </p>
          </div>
        )}

        {/* Completed State */}
        {document.status === 'COMPLETED' && (
          <>
            {/* Extracted Text */}
            {document.extractedText && (
              <div className="detail-section slide-up">
                <h3 className="detail-section-title">
                  <FileText size={20} style={{ color: 'var(--accent-cyan)' }} />
                  Extracted Text
                </h3>
                <div className="text-content">
                  {document.extractedText}
                </div>
              </div>
            )}

            {/* Metadata */}
            {metadata && Object.keys(metadata).length > 0 && (
              <div className="detail-section slide-up">
                <h3 className="detail-section-title">
                  <FileBarChart size={20} style={{ color: 'var(--accent-purple)' }} />
                  Metadata
                </h3>
                <div className="glass-card" style={{ overflow: 'hidden' }}>
                  <table className="metadata-table">
                    <thead>
                      <tr>
                        <th>Property</th>
                        <th>Value</th>
                      </tr>
                    </thead>
                    <tbody>
                      {Object.entries(metadata).map(([key, value]) => (
                        <tr key={key}>
                          <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem', color: 'var(--accent-cyan)' }}>
                            {key}
                          </td>
                          <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>
                            {typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* Actions */}
            <div className="detail-actions slide-up" style={{ marginTop: '1.5rem' }}>
              <button className="btn btn-primary" onClick={handleDownload}>
                <Download size={18} />
                Download Original
              </button>

              <button
                className="btn btn-secondary"
                onClick={handleGenerateReport}
                disabled={reportLoading}
              >
                {reportLoading ? (
                  <div className="loading-spinner" style={{ width: 18, height: 18, borderWidth: 2 }} />
                ) : (
                  <FileBarChart size={18} />
                )}
                {reportLoading ? 'Generating...' : 'Generate Report'}
              </button>

              {reportUrl && (
                <button className="btn btn-primary" onClick={handleDownloadReport}>
                  <Download size={18} />
                  Download Report
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default DocumentDetailPage;
