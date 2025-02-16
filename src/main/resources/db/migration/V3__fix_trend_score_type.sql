-- First, handle any null or invalid values
UPDATE trend_data SET trend_score = '0' WHERE trend_score IS NULL OR trend_score = '';

-- Now alter the column type with explicit casting
ALTER TABLE trend_data 
  ALTER COLUMN trend_score TYPE double precision USING (CASE 
    WHEN trend_score ~ E'^\\d*\\.?\\d+$' THEN trend_score::double precision 
    ELSE 0.0 
  END);
