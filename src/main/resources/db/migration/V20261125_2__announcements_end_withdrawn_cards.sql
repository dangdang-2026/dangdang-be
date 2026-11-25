-- #156 「카드를 내리면 걸린 소식도 끝내기」 배포 전에 이미 내린 카드에 걸린 열린 소식을 끝낸다.
-- 안 끝내면 나중에 카드를 다시 올릴 때 그 옛 소식이 저절로 되살아난다.
-- 규칙은 AnnouncementRepository.endAllForCard와 같다: 끝 = 카드 내린 시각, 시작 전이면 끝 = 시작,
-- 이미 그보다 일찍 끝난 줄은 그대로.
-- 내린 시각은 withdrawn_at. 앱은 내릴 때 늘 채우지만(ContentCard.withdraw) DB로 상태만 바꾼 줄이 있으면
-- updated_at을 쓴다. 내리기가 updated_at도 같은 시각으로 바꾸고, 내린 카드는 정정·검수가 막혀 그 뒤로 안 바뀌며,
-- NOT NULL이라 비지 않는다. 배포 시각(now())은 실제 내린 때와 상관없어서 쓰지 않는다.
UPDATE announcements a
   SET active_to = GREATEST(a.active_from, COALESCE(c.withdrawn_at, c.updated_at))
  FROM content_cards c
 WHERE c.id = a.content_card_id
   AND c.status = 'WITHDRAWN'
   AND (a.active_to IS NULL
        OR (a.active_to > COALESCE(c.withdrawn_at, c.updated_at) AND a.active_to > a.active_from));

-- 카드를 내릴 때마다 그 카드를 건 소식을 찾는다 (PostgreSQL은 FK에 인덱스를 저절로 만들지 않는다)
CREATE INDEX ix_announcements_card ON announcements (content_card_id) WHERE content_card_id IS NOT NULL;
