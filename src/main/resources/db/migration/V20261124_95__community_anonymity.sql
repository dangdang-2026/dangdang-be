-- U-20 · U-13 · 댓글 ⋯: 익명 글·댓글에서도 「이 사람 글 안 보기」 (⑩ A)
-- 익명 글·댓글로 막은 줄은 차단 목록에서 닉네임 대신 「익명 사용자」로 보인다
ALTER TABLE profile_blocks ADD COLUMN via_anonymous BOOLEAN NOT NULL DEFAULT false;

-- 같은 사람을 이름으로 한 번, 익명 글로 한 번 막으면 두 줄
-- (한 줄로 합치면 목록에서 「익명 사용자」가 닉네임으로 바뀌어 익명 글쓴이가 누군지 드러난다)
ALTER TABLE profile_blocks DROP CONSTRAINT uk_profile_blocks;
ALTER TABLE profile_blocks ADD CONSTRAINT uk_profile_blocks UNIQUE (profile_id, blocked_profile_id, via_anonymous);

-- U-02b 「익명 1·2」 (⑩ B): 이 글에서 그 사람이 처음 익명 댓글을 달 때 정한 번호를 댓글 줄에 적어 둔다
-- 적어 두니까 댓글을 지우거나, 가리거나, 쓴 사람이 탈퇴해도(profile_id가 NULL) 남의 번호가 밀리지 않는다
-- 익명 글의 글쓴이가 단 댓글은 번호 대신 「글쓴이」라 NULL
ALTER TABLE comments ADD COLUMN anonymous_no INT;

-- 이미 있는 익명 댓글: 글마다 처음 익명 댓글을 단 순서대로 1, 2, …
UPDATE comments c
SET anonymous_no = f.no
FROM (SELECT c2.post_id, c2.profile_id,
             row_number() OVER (PARTITION BY c2.post_id ORDER BY min(c2.id)) AS no
      FROM comments c2
      JOIN posts p ON p.id = c2.post_id
      WHERE c2.is_anonymous
        AND c2.profile_id IS NOT NULL
        AND NOT (p.is_anonymous AND p.profile_id IS NOT NULL AND p.profile_id = c2.profile_id)
      GROUP BY c2.post_id, c2.profile_id) f
WHERE c.post_id = f.post_id
  AND c.profile_id = f.profile_id
  AND c.is_anonymous;
