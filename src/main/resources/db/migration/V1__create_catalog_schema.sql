CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE authors (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(255) NOT NULL,
  bio TEXT,
  avatar VARCHAR(500),
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(100) NOT NULL,
  slug VARCHAR(100) UNIQUE NOT NULL,
  icon VARCHAR(100),
  parent_id UUID REFERENCES categories(id)
);

CREATE TABLE tags (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE books (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(500) NOT NULL,
  slug VARCHAR(500) UNIQUE NOT NULL,
  description TEXT NOT NULL,
  isbn VARCHAR(20) UNIQUE,
  cover_image VARCHAR(500),
  preview_images TEXT[],
  published_at TIMESTAMP,
  language VARCHAR(10) DEFAULT 'pt',
  pages INT,
  publisher VARCHAR(255),
  edition VARCHAR(50),
  type VARCHAR(20) NOT NULL CHECK(type IN ('PHYSICAL','EBOOK','BOTH')),
  price DECIMAL(10,2) NOT NULL,
  subscription_only BOOLEAN DEFAULT false,
  file_key VARCHAR(500),
  file_size_bytes BIGINT,
  format VARCHAR(10) CHECK(format IN ('PDF','EPUB','MOBI')),
  stock_quantity INT DEFAULT 0,
  weight DECIMAL(6,2),
  dimensions VARCHAR(50),
  is_active BOOLEAN DEFAULT true,
  is_featured BOOLEAN DEFAULT false,
  average_rating DECIMAL(3,2) DEFAULT 0,
  review_count INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE book_authors (
  book_id UUID REFERENCES books(id) ON DELETE CASCADE,
  author_id UUID REFERENCES authors(id),
  PRIMARY KEY(book_id, author_id)
);

CREATE TABLE book_categories (
  book_id UUID REFERENCES books(id) ON DELETE CASCADE,
  category_id UUID REFERENCES categories(id),
  PRIMARY KEY(book_id, category_id)
);

CREATE TABLE book_tags (
  book_id UUID REFERENCES books(id) ON DELETE CASCADE,
  tag_id UUID REFERENCES tags(id),
  PRIMARY KEY(book_id, tag_id)
);

CREATE TABLE book_relations (
  source_id UUID REFERENCES books(id) ON DELETE CASCADE,
  target_id UUID REFERENCES books(id) ON DELETE CASCADE,
  score FLOAT DEFAULT 1.0,
  PRIMARY KEY(source_id, target_id)
);

CREATE TABLE reviews (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  user_name VARCHAR(255),
  book_id UUID REFERENCES books(id) ON DELETE CASCADE,
  rating INT NOT NULL CHECK(rating BETWEEN 1 AND 5),
  comment TEXT,
  created_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(user_id, book_id)
);

-- Seed default categories
INSERT INTO categories (name, slug, icon) VALUES
  ('Ficção', 'ficcao', '📚'),
  ('Não-Ficção', 'nao-ficcao', '📖'),
  ('Educação', 'educacao', '🎓'),
  ('Negócios', 'negocios', '💼'),
  ('Tecnologia', 'tecnologia', '💻'),
  ('Romance', 'romance', '❤️'),
  ('Infantil', 'infantil', '🧒'),
  ('História', 'historia', '🏛️');
