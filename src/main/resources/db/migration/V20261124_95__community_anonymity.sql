-- U-20 · U-13 · 댓글 ⋯: 익명 글·댓글에서도 「이 사람 글 안 보기」 (⑩ A)
-- 익명 글·댓글로 막은 줄은 차단 목록에서 닉네임 대신 「익명 사용자」로 보인다
ALTER TABLE profile_blocks ADD COLUMN via_anonymous BOOLEAN NOT NULL DEFAULT false;

-- 같은 사람을 이름으로 한 번, 익명 글로 한 번 막으면 두 줄
-- (한 줄로 합치면 목록에서 「익명 사용자」가 닉네임으로 바뀌어 익명 글쓴이가 누군지 드러난다)
ALTER TABLE profile_blocks DROP CONSTRAINT uk_profile_blocks;
ALTER TABLE profile_blocks ADD CONSTRAINT uk_profile_blocks UNIQUE (profile_id, blocked_profile_id, via_anonymous);

-- 이 PR 전에 막은 줄은 그 사람 익명 글·댓글까지 가렸다 → 배포 뒤에도 계속 가리도록 익명 줄도 같이 넣는다
-- (그래서 예전에 막은 사람은 차단 목록에 「익명 사용자」 줄이 하나 더 보인다. 새로 막는 줄은 이름·익명을 따로)
INSERT INTO profile_blocks (profile_id, blocked_profile_id, via_anonymous, created_at)
SELECT profile_id, blocked_profile_id, true, created_at
FROM profile_blocks
WHERE NOT via_anonymous
ON CONFLICT (profile_id, blocked_profile_id, via_anonymous) DO NOTHING;

-- U-02b 「익명 1·2」 (⑩ B): 이 글에서 그 사람이 처음 익명 댓글을 달 때 정한 번호를 댓글 줄에 적어 둔다
-- 적어 두니까 댓글을 지우거나, 가리거나, 쓴 사람이 탈퇴해도(profile_id가 NULL) 남의 번호가 밀리지 않는다
-- 익명 글의 글쓴이가 단 댓글은 번호 대신 「글쓴이」라 NULL
ALTER TABLE comments ADD COLUMN anonymous_no INT;

-- 예전 댓글: 익명 글의 글쓴이가 자기 글에 이름으로 단 댓글은 익명으로 바꾼다 (U-18, 닉네임으로 익명 글쓴이가 드러나지 않게)
-- 글쓴이 댓글이라 번호 없이 「글쓴이」 라벨만 붙는다
UPDATE comments c
SET is_anonymous = true
FROM posts p
WHERE p.id = c.post_id
  AND p.is_anonymous
  AND p.profile_id IS NOT NULL
  AND c.profile_id = p.profile_id
  AND NOT c.is_anonymous;

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

-- U-04 · M-16 예전 닉네임: 띄어쓰기 두 칸 이상은 한 칸으로 (이제 새로 만들거나 고칠 때 두 칸을 막으니, 남은 것도 맞춘다)
-- 한 칸으로 바꾼 이름이 아직 없으면 그대로 바꾼다. 같은 이름으로 바뀌는 줄이 여럿이면 먼저 만든 한 줄만
UPDATE community_profiles cp
SET nickname = regexp_replace(cp.nickname, ' {2,}', ' ', 'g')
WHERE cp.nickname LIKE '%  %'
  AND NOT EXISTS (SELECT 1 FROM community_profiles o
                  WHERE o.nickname = regexp_replace(cp.nickname, ' {2,}', ' ', 'g'))
  AND cp.id = (SELECT min(o.id) FROM community_profiles o
               WHERE o.nickname LIKE '%  %'
                 AND regexp_replace(o.nickname, ' {2,}', ' ', 'g') = regexp_replace(cp.nickname, ' {2,}', ' ', 'g'));

-- 남은 줄(한 칸으로 바꾸면 남의 이름과 같아지는 줄)은 한 칸으로 바꾸고 뒤에 프로필 번호를 붙여 겹치지 않게
-- 10자를 넘을 수 있지만 칸은 20자라 들어가고, 글자 수 규칙은 다음에 닉네임을 고칠 때만 본다
UPDATE community_profiles cp
SET nickname = regexp_replace(cp.nickname, ' {2,}', ' ', 'g') || cp.id
WHERE cp.nickname LIKE '%  %'
  AND NOT EXISTS (SELECT 1 FROM community_profiles o
                  WHERE o.nickname = regexp_replace(cp.nickname, ' {2,}', ' ', 'g') || cp.id);
