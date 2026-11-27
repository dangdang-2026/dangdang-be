-- 음식 이름 임베딩 (AI 서버 /internal/embeddings, text-embedding-3-small, 1536차원)
-- 사진 분석 후보가 글자 검색(LIKE)으로 0건일 때만 뜻이 가까운 음식을 찾는 데 쓴다
-- 빈 칸은 FoodEmbeddingFiller가 서버가 뜰 때 채운다 (FOOD_EMBEDDING_FILL_ENABLED=true인 서버만)
ALTER TABLE foods ADD COLUMN name_embedding vector(1536);

-- 코사인 거리용 인덱스. 지금 2만 줄은 없어도 수십 ms지만 음식이 늘 것에 대비
CREATE INDEX idx_foods_name_embedding ON foods USING hnsw (name_embedding vector_cosine_ops);
