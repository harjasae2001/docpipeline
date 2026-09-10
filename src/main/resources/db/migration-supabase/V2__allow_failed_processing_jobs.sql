ALTER TABLE app.processing_jobs
    DROP CONSTRAINT processing_jobs_status_check;

ALTER TABLE app.processing_jobs
    ADD CONSTRAINT processing_jobs_status_check CHECK (
        status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'RETRYING', 'FAILED', 'DEAD_LETTER')
    );
