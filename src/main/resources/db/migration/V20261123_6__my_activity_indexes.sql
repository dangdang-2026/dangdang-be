-- 내 커뮤니티 활동 (M-24~M-26 · M-14) — 「내가 쓴 글·댓글·공감한 글」을 사람별로 최신순으로 읽는다
-- 지금은 글이 적어서 없어도 빠르지만, 표가 커지면 표 전체를 훑게 되니 미리 둔다
-- (프로필을 지울 때 posts·comments의 profile_id 를 NULL 로 바꾸는 것도 이 인덱스로 빨라진다)
CREATE INDEX ix_posts_profile_latest ON posts (profile_id, created_at DESC, id DESC);
CREATE INDEX ix_comments_profile_latest ON comments (profile_id, created_at DESC, id DESC);
CREATE INDEX ix_post_reactions_profile_latest ON post_reactions (profile_id, created_at DESC, id DESC);
