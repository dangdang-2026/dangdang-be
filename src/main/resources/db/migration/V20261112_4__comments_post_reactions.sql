-- 댓글
CREATE TABLE comments (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  profile_id BIGINT REFERENCES community_profiles(id) ON DELETE SET NULL,
  parent_id BIGINT REFERENCES comments(id),       -- 답글이면 부모 댓글
  content TEXT NOT NULL,
  is_anonymous BOOLEAN NOT NULL DEFAULT false,
  hidden_reason VARCHAR(50),                      -- 가려진 이유
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);

-- 글 공감
CREATE TABLE post_reactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES community_profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_post_reactions UNIQUE (post_id, profile_id)     -- 한 사람이 한 글에 한 번만
);

-- 댓글 공감
CREATE TABLE comment_reactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comment_id BIGINT NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES community_profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_comment_reactions UNIQUE (comment_id, profile_id)
);

CREATE INDEX ix_comments_post_time ON comments (post_id, created_at, id);       -- 글의 댓글 목록
