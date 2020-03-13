CREATE TABLE todos (
  id UUID NOT NULL DEFAULT gen_random_uuid(),
  title VARCHAR(128),
  description TEXT,
  created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  due_date TIMESTAMPTZ,
  complete BOOLEAN,
  PRIMARY KEY(id)
);

GRANT ALL ON todos TO introduction;