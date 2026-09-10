CREATE SCHEMA IF NOT EXISTS app;

REVOKE ALL ON SCHEMA app FROM anon, authenticated;

CREATE TABLE app.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL DEFAULT '',
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT profiles_role_check CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE app.documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app.profiles(id) ON DELETE CASCADE,
    file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT,
    storage_bucket VARCHAR(255) NOT NULL DEFAULT 'docpipeline-private',
    storage_key VARCHAR(1024) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_UPLOAD',
    textract_job_id VARCHAR(255),
    textract_staging_key VARCHAR(1024),
    extracted_text TEXT,
    metadata JSONB,
    last_error TEXT,
    uploaded_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT documents_status_check CHECK (
        status IN ('PENDING_UPLOAD', 'UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED', 'ARCHIVED')
    )
);

CREATE TABLE app.processing_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES app.documents(id) ON DELETE CASCADE,
    result_type VARCHAR(50) NOT NULL,
    content TEXT,
    confidence DOUBLE PRECISION,
    page_number INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE app.processing_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL UNIQUE REFERENCES app.documents(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    correlation_id UUID NOT NULL UNIQUE,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT processing_jobs_status_check CHECK (
        status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'RETRYING', 'DEAD_LETTER')
    )
);

CREATE INDEX idx_documents_user_created ON app.documents(user_id, created_at DESC);
CREATE INDEX idx_documents_status ON app.documents(status);
CREATE INDEX idx_documents_textract_job ON app.documents(textract_job_id) WHERE textract_job_id IS NOT NULL;
CREATE INDEX idx_processing_results_document ON app.processing_results(document_id);
CREATE INDEX idx_processing_jobs_status ON app.processing_jobs(status, updated_at);

ALTER TABLE app.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.processing_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.processing_jobs ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION app.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    INSERT INTO app.profiles (id, full_name, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data ->> 'full_name', ''),
        COALESCE(NEW.raw_app_meta_data ->> 'app_role', 'USER')
    )
    ON CONFLICT (id) DO UPDATE
    SET full_name = EXCLUDED.full_name,
        updated_at = NOW();
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION app.handle_new_auth_user() FROM PUBLIC, anon, authenticated;

CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION app.handle_new_auth_user();

CREATE EXTENSION IF NOT EXISTS pgmq;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pgmq.list_queues() WHERE queue_name = 'document_processing') THEN
        PERFORM pgmq.create('document_processing');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pgmq.list_queues() WHERE queue_name = 'document_processing_dlq') THEN
        PERFORM pgmq.create('document_processing_dlq');
    END IF;
END;
$$;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'docpipeline-private',
    'docpipeline-private',
    FALSE,
    52428800,
    ARRAY['application/pdf', 'text/csv', 'image/jpeg', 'image/png']
)
ON CONFLICT (id) DO UPDATE
SET public = FALSE,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

CREATE POLICY "users_read_own_docpipeline_objects"
ON storage.objects FOR SELECT TO authenticated
USING (
    bucket_id = 'docpipeline-private'
    AND (storage.foldername(name))[2] = (SELECT auth.uid())::TEXT
);

CREATE POLICY "users_insert_own_docpipeline_objects"
ON storage.objects FOR INSERT TO authenticated
WITH CHECK (
    bucket_id = 'docpipeline-private'
    AND (storage.foldername(name))[2] = (SELECT auth.uid())::TEXT
);

CREATE POLICY "users_update_own_docpipeline_objects"
ON storage.objects FOR UPDATE TO authenticated
USING (
    bucket_id = 'docpipeline-private'
    AND (storage.foldername(name))[2] = (SELECT auth.uid())::TEXT
)
WITH CHECK (
    bucket_id = 'docpipeline-private'
    AND (storage.foldername(name))[2] = (SELECT auth.uid())::TEXT
);

CREATE POLICY "users_delete_own_docpipeline_objects"
ON storage.objects FOR DELETE TO authenticated
USING (
    bucket_id = 'docpipeline-private'
    AND (storage.foldername(name))[2] = (SELECT auth.uid())::TEXT
);
