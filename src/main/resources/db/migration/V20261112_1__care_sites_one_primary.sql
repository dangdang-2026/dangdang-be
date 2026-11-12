-- 대표 병원은 사용자당 하나 (삭제 안 된 것 중에서)
-- 코드로만 지키면 대표 지정 요청 2개가 동시에 올 때 대표가 2개가 될 수 있어서 DB에서도 막음
CREATE UNIQUE INDEX ux_care_sites_primary ON care_sites (user_id) WHERE is_primary AND deleted_at IS NULL;
