-- 테스트용 기본 데이터 (PostgreSQL)
-- 반복 실행을 고려하여 ON CONFLICT DO NOTHING 패턴 사용

-- 사용자 (Feed/Moderation 통합 테스트에서 사용)
INSERT INTO users (id, email, name, password, provider, created_at, updated_at)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'tester@matjom.com',
    '테스트 사용자',
    'dummy-password',
    'LOCAL',
    now(),
    now()
)
ON CONFLICT (id) DO NOTHING;

-- 장소 1 (리뷰 테스트)
INSERT INTO places (
    place_id, name, lat, lng, category, provider_id, phone_number,
    working_hours, break_time, opened_at, opened_at_source, biz_status,
    addr_sido, addr_sigungu, addr_eupmyeondong, addr_street, addr_detail,
    location, created_at, updated_at
)
VALUES (
    1,
    '테스트 식당 A',
    37.5665,
    126.9780,
    ARRAY['KOREAN'],
    'TEST-PLACE-1',
    '02-0000-0000',
    '{"mon":{"open":"09:00","close":"18:00"}}'::jsonb,
    '{"mon":{"start":"12:00","end":"13:00"}}'::jsonb,
    '2020-01-01',
    '{"source":"manual"}'::jsonb,
    'OPEN',
    '서울특별시',
    '중구',
    '명동',
    '을지로',
    '1가',
    ST_SetSRID(ST_MakePoint(126.9780, 37.5665), 4326),
    now(),
    now()
)
ON CONFLICT (place_id) DO NOTHING;

-- 장소 2 (좋아요 테스트)
INSERT INTO places (
    place_id, name, lat, lng, category, provider_id, phone_number,
    working_hours, break_time, opened_at, opened_at_source, biz_status,
    addr_sido, addr_sigungu, addr_eupmyeondong, addr_street, addr_detail,
    location, created_at, updated_at
)
VALUES (
    2,
    '테스트 식당 B',
    35.1796,
    129.0756,
    ARRAY['FUSION'],
    'TEST-PLACE-2',
    '051-000-0000',
    '{"mon":{"open":"10:00","close":"20:00"}}'::jsonb,
    '{"mon":{"start":"15:00","end":"16:00"}}'::jsonb,
    '2021-03-15',
    '{"source":"manual"}'::jsonb,
    'OPEN',
    '부산광역시',
    '중구',
    '광복동',
    '광복로',
    '10-1',
    ST_SetSRID(ST_MakePoint(129.0756, 35.1796), 4326),
    now(),
    now()
)
ON CONFLICT (place_id) DO NOTHING;

-- 방문 기록 (리뷰/좋아요 테스트용)
INSERT INTO visits (
    visit_id, user_id, place_id, state, client_mode, started_at,
    arrived_at, cancelled_at, expired_at, last_pos_at, dwell_started_at,
    last_lat, last_lng, last_accuracy_m, meta, created_at, updated_at
)
VALUES
    (
        10,
        '11111111-1111-1111-1111-111111111111',
        1,
        'ARRIVED',
        'NAVIGATION',
        now() - interval '2 hours',
        now() - interval '90 minutes',
        NULL,
        NULL,
        NULL,
        NULL,
        37.5665,
        126.9780,
        5.0,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        20,
        '11111111-1111-1111-1111-111111111111',
        2,
        'ARRIVED',
        'NAVIGATION',
        now() - interval '3 hours',
        now() - interval '100 minutes',
        NULL,
        NULL,
        NULL,
        NULL,
        35.1796,
        129.0756,
        7.0,
        '{}'::jsonb,
        now(),
        now()
    )
ON CONFLICT (visit_id) DO NOTHING;

