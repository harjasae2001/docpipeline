function StatusBadge({ status }) {
  const statusMap = {
    PENDING_UPLOAD: { label: 'Pending', className: 'status-pending' },
    UPLOADED: { label: 'Uploaded', className: 'status-uploaded' },
    PROCESSING: { label: 'Processing', className: 'status-processing' },
    COMPLETED: { label: 'Completed', className: 'status-completed' },
    FAILED: { label: 'Failed', className: 'status-failed' },
  };

  const info = statusMap[status] || { label: status || 'Unknown', className: '' };

  return (
    <span className={`status-badge ${info.className}`}>
      {info.label}
    </span>
  );
}

export default StatusBadge;
