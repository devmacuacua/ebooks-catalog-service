ALTER TABLE books
  ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
  ADD COLUMN IF NOT EXISTS partner_id       UUID,
  ADD COLUMN IF NOT EXISTS partner_name     VARCHAR(255),
  ADD COLUMN IF NOT EXISTS submitted_at     TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_books_partner_id ON books(partner_id);
CREATE INDEX IF NOT EXISTS idx_books_partner_status ON books(partner_id, status);
