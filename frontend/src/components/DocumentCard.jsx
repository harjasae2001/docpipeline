import { useNavigate } from 'react-router-dom';
import { FileText, Image, Table2, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';
import StatusBadge from './StatusBadge';
import { formatFileSize, timeAgo, getFileIcon } from '../utils/format';
import { deleteDocument } from '../services/api';

const iconMap = {
  'file-text': FileText,
  'image': Image,
  'table': Table2,
};

function DocumentCard({ document, onDelete }) {
  const navigate = useNavigate();

  const iconType = getFileIcon(document.contentType);
  const IconComponent = iconMap[iconType] || FileText;

  const handleClick = () => {
    navigate(`/documents/${document.id}`);
  };

  const handleDelete = async (e) => {
    e.stopPropagation();
    const confirmed = window.confirm(
      `Are you sure you want to delete "${document.fileName}"? This action cannot be undone.`
    );
    if (!confirmed) return;

    try {
      await deleteDocument(document.id);
      toast.success(`"${document.fileName}" deleted.`);
      if (onDelete) onDelete(document.id);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to delete document.');
    }
  };

  return (
    <div className="glass-card document-card slide-up" onClick={handleClick}>
      <div className="document-card-header">
        <IconComponent size={24} className="document-card-icon" />
        <span className="document-card-name" title={document.fileName}>
          {document.fileName}
        </span>
      </div>

      <div className="document-card-meta">
        <StatusBadge status={document.status} />
        <span className="document-card-info">
          {formatFileSize(document.fileSize)}
        </span>
      </div>

      <div className="document-card-meta">
        <span className="document-card-info">
          {timeAgo(document.createdAt)}
        </span>
        <div className="document-card-actions">
          <button
            className="btn btn-danger btn-sm"
            onClick={handleDelete}
            title="Delete document"
            aria-label={`Delete ${document.fileName}`}
          >
            <Trash2 size={14} />
          </button>
        </div>
      </div>
    </div>
  );
}

export default DocumentCard;
