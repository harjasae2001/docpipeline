CREATE TABLE processing_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    result_type VARCHAR(50) NOT NULL,
    content TEXT,
    confidence DOUBLE PRECISION,
    page_number INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_processing_results_document_id ON processing_results(document_id);
CREATE INDEX idx_processing_results_result_type ON processing_results(result_type);
