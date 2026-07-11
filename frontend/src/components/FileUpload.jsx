import { useState, useRef, useCallback } from 'react';
import { Upload, CheckCircle, AlertCircle, FileUp } from 'lucide-react';
import toast from 'react-hot-toast';
import { getPresignedUrl, uploadToS3, confirmUpload } from '../services/api';

const ACCEPTED_TYPES = {
  'application/pdf': '.pdf',
  'text/csv': '.csv',
  'image/jpeg': '.jpg,.jpeg',
  'image/png': '.png',
};

const ACCEPTED_EXTENSIONS = '.pdf,.csv,.jpg,.jpeg,.png';

function FileUpload({ onUploadComplete }) {
  const [state, setState] = useState('idle'); // idle | dragover | uploading | success | error
  const [progress, setProgress] = useState(0);
  const [fileName, setFileName] = useState('');
  const fileInputRef = useRef(null);

  const resetState = useCallback(() => {
    setTimeout(() => {
      setState('idle');
      setProgress(0);
      setFileName('');
    }, 2000);
  }, []);

  const handleUpload = useCallback(async (file) => {
    if (!file) return;

    // Validate file type
    const validTypes = Object.keys(ACCEPTED_TYPES);
    if (!validTypes.includes(file.type)) {
      toast.error('Unsupported file type. Please upload PDF, CSV, JPG, or PNG files.');
      return;
    }

    // Validate file size (50MB max)
    if (file.size > 50 * 1024 * 1024) {
      toast.error('File too large. Maximum size is 50MB.');
      return;
    }

    setFileName(file.name);
    setState('uploading');
    setProgress(0);

    try {
      // Step 1: Get presigned URL
      const { data } = await getPresignedUrl(file.name, file.type);
      const { uploadUrl, documentId } = data;
      // Step 2: Upload to S3
      await uploadToS3(uploadUrl, file, file.type, (percent) => {
        setProgress(percent);
      });

      // Step 3: Confirm upload
      await confirmUpload(documentId);

      setState('success');
      toast.success(`"${file.name}" uploaded successfully!`);
      if (onUploadComplete) onUploadComplete();
      resetState();
    } catch (err) {
      setState('error');
      toast.error(err?.response?.data?.message || err.message || 'Upload failed. Please try again.');
      resetState();
    }

    // Reset file input
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }, [onUploadComplete, resetState]);

  const handleDragOver = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    if (state !== 'uploading') setState('dragover');
  }, [state]);

  const handleDragLeave = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    if (state !== 'uploading') setState('idle');
  }, [state]);

  const handleDrop = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    if (state === 'uploading') return;
    setState('idle');
    const file = e.dataTransfer.files?.[0];
    handleUpload(file);
  }, [state, handleUpload]);

  const handleFileSelect = useCallback((e) => {
    const file = e.target.files?.[0];
    handleUpload(file);
  }, [handleUpload]);

  const handleClick = useCallback(() => {
    if (state !== 'uploading') {
      fileInputRef.current?.click();
    }
  }, [state]);

  const renderIcon = () => {
    switch (state) {
      case 'success':
        return <CheckCircle size={48} className="upload-zone-icon" style={{ color: 'var(--success)' }} />;
      case 'error':
        return <AlertCircle size={48} className="upload-zone-icon" style={{ color: 'var(--error)' }} />;
      case 'uploading':
        return <FileUp size={48} className="upload-zone-icon" style={{ color: 'var(--accent-purple)' }} />;
      default:
        return <Upload size={48} className="upload-zone-icon" />;
    }
  };

  const renderText = () => {
    switch (state) {
      case 'dragover':
        return <p className="upload-zone-text">Drop your file here</p>;
      case 'uploading':
        return (
          <>
            <p className="upload-zone-text" style={{ fontWeight: 500 }}>{fileName}</p>
            <div className="progress-container">
              <div className="progress-bar" style={{ width: `${progress}%` }} />
            </div>
            <p className="upload-zone-hint" style={{ marginTop: '0.75rem' }}>{progress}% uploaded</p>
          </>
        );
      case 'success':
        return <p className="upload-zone-text" style={{ color: 'var(--success)' }}>Upload complete!</p>;
      case 'error':
        return <p className="upload-zone-text" style={{ color: 'var(--error)' }}>Upload failed. Try again.</p>;
      default:
        return (
          <>
            <p className="upload-zone-text">
              <strong>Click to upload</strong> or drag and drop
            </p>
            <p className="upload-zone-hint">PDF, CSV, JPG, PNG — Max 50MB</p>
          </>
        );
    }
  };

  return (
    <div
      className={`upload-zone ${state}`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      onClick={handleClick}
      role="button"
      tabIndex={0}
      aria-label="Upload file"
    >
      {renderIcon()}
      {renderText()}
      <input
        ref={fileInputRef}
        type="file"
        accept={ACCEPTED_EXTENSIONS}
        onChange={handleFileSelect}
        style={{ display: 'none' }}
        aria-hidden="true"
      />
    </div>
  );
}

export default FileUpload;
