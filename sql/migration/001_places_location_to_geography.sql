-- 1.1 GEOGRAPHY 전환
-- 목적: places.location 컬럼을 geometry(Point,4326)에서 geography(Point,4326)로 변환
-- 실행 전 준비사항: DB 백업, 앱 중단 필요 없음(트랜잭션 내 처리)

BEGIN;

-- 1) 임시 컬럼 추가
ALTER TABLE places
    ADD COLUMN location_geog geography(Point,4326);

-- 2) 기존 geometry 데이터를 geography로 변환하여 채우기
UPDATE places
SET location_geog = location::geography;

-- 3) 기존 인덱스 제거
DROP INDEX IF EXISTS idx_places_location;

-- 4) 기존 컬럼 삭제 및 이름 변경
ALTER TABLE places
    DROP COLUMN location;

ALTER TABLE places
    RENAME COLUMN location_geog TO location;

-- 5) GiST 인덱스 재생성
CREATE INDEX idx_places_location
    ON places
    USING gist (location);

COMMIT;
