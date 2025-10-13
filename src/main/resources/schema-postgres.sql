-- Users
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL,
    name TEXT NOT NULL,
    password TEXT,
    provider VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_user_email_provider UNIQUE (email, provider),
    CONSTRAINT chk_password_required CHECK (
        (provider = 'LOCAL' AND password IS NOT NULL) OR
        (provider <> 'LOCAL' AND password IS NULL)
    )
);

CREATE TABLE IF NOT EXISTS deleted_users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL,
    name TEXT NOT NULL,
    password TEXT,
    provider VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_deleted_user_password_required CHECK (
        (provider = 'LOCAL' AND password IS NOT NULL) OR
        (provider <> 'LOCAL' AND password IS NULL)
    )
);
-- Places
CREATE TABLE IF NOT EXISTS places (
    place_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    lat NUMERIC(9,6) NOT NULL,
    lng NUMERIC(9,6) NOT NULL,
    category TEXT[] NOT NULL,
    provider_id VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    working_hours JSONB NOT NULL,
    break_time JSONB NOT NULL,
    opened_at DATE NOT NULL,
    opened_at_source JSONB NOT NULL,
    biz_status VARCHAR(20) NOT NULL,
    addr_sido VARCHAR(20) NOT NULL,
    addr_sigungu VARCHAR(30) NOT NULL,
    addr_eupmyeondong VARCHAR(80) NOT NULL,
    addr_street VARCHAR(100) NOT NULL,
    addr_detail VARCHAR(100) NOT NULL,
    location geometry(Point,4326),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_places_provider_id UNIQUE (provider_id)
);

CREATE INDEX IF NOT EXISTS idx_places_lower_name ON places (lower(name));
CREATE INDEX IF NOT EXISTS idx_places_category ON places USING GIN (category);
CREATE INDEX IF NOT EXISTS idx_places_location ON places USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_places_sido ON places (addr_sido);
CREATE INDEX IF NOT EXISTS idx_places_sigungu ON places (addr_sigungu);
CREATE INDEX IF NOT EXISTS idx_places_eupmyeondong ON places (addr_eupmyeondong);

-- Visits
CREATE TABLE IF NOT EXISTS visits (
    visit_id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    place_id BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    client_mode VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    arrived_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    last_pos_at TIMESTAMPTZ,
    dwell_started_at TIMESTAMPTZ,
    last_lat NUMERIC(9,6),
    last_lng NUMERIC(9,6),
    last_accuracy_m NUMERIC(6,2),
    meta JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_visits_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_visits_place FOREIGN KEY (place_id) REFERENCES places(place_id),
    CONSTRAINT chk_visit_state CHECK (state IN ('ACTIVE', 'ARRIVED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_visit_client_mode CHECK (client_mode IN ('NAVIGATION', 'IDLE'))
);

CREATE INDEX IF NOT EXISTS idx_visits_user ON visits (user_id);
CREATE INDEX IF NOT EXISTS idx_visits_place ON visits (place_id);
CREATE INDEX IF NOT EXISTS idx_visits_started_at ON visits (started_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_visits_active_user ON visits (user_id) WHERE state = 'ACTIVE';

-- Visit positions
CREATE TABLE IF NOT EXISTS visit_positions (
    pos_id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL,
    lat NUMERIC(9,6) NOT NULL,
    lng NUMERIC(9,6) NOT NULL,
    accuracy_m NUMERIC(6,2),
    mode VARCHAR(20) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_visit_positions_visit FOREIGN KEY (visit_id) REFERENCES visits(visit_id) ON DELETE CASCADE,
    CONSTRAINT chk_visit_position_mode CHECK (mode IN ('NAVIGATION', 'IDLE'))
);

CREATE INDEX IF NOT EXISTS idx_visit_positions_visit ON visit_positions (visit_id);
CREATE INDEX IF NOT EXISTS idx_visit_positions_visit_received ON visit_positions (visit_id, received_at);

-- User place first arrivals
CREATE TABLE IF NOT EXISTS user_place_first_arrivals (
    user_id UUID NOT NULL,
    place_id BIGINT NOT NULL,
    first_arrived_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, place_id),
    CONSTRAINT fk_upfa_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_upfa_place FOREIGN KEY (place_id) REFERENCES places(place_id)
);

-- Feed & Stats 최종 DDL (9월24일 BaseEntity 적용 수정)
-- 팀장 테이블(users, places, visits, visit_positions, user_place_first_arrivals) 절대 수정 금지

-- Reviews (리뷰 본체)
-- 9월 24일 수정: BaseEntity 상속으로 표준 타임스탬프 적용
CREATE TABLE IF NOT EXISTS reviews (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    place_id BIGINT NOT NULL,
    visit_id BIGINT NOT NULL,
    user_name VARCHAR(50) NOT NULL,
    text VARCHAR(140) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), -- 9월 24일 수정: BaseEntity 표준
    updated_at TIMESTAMPTZ, -- 9월 24일 수정: BaseEntity 표준
    deleted_at TIMESTAMPTZ, -- 9월 24일 수정: BaseEntity 표준
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reviews_place FOREIGN KEY (place_id) REFERENCES places(place_id),
    CONSTRAINT fk_reviews_visit FOREIGN KEY (visit_id) REFERENCES visits(visit_id),
    CONSTRAINT chk_review_text_length CHECK (char_length(text) BETWEEN 1 AND 140),
    CONSTRAINT uq_review_visit UNIQUE (visit_id) -- 방문당 리뷰 1개 제한
    );

CREATE INDEX IF NOT EXISTS idx_reviews_user ON reviews (user_id);
CREATE INDEX IF NOT EXISTS idx_reviews_place ON reviews (place_id);
CREATE INDEX IF NOT EXISTS idx_reviews_visit ON reviews (visit_id);
CREATE INDEX IF NOT EXISTS idx_reviews_created_at ON reviews (created_at);
CREATE INDEX IF NOT EXISTS idx_reviews_place_active ON reviews (place_id) WHERE deleted_at IS NULL;

-- Daily Likes (일일 좋아요)
-- 9월 24일 수정: BaseEntity 상속으로 표준 타임스탬프 적용
CREATE TABLE IF NOT EXISTS daily_likes (
                                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    place_id BIGINT NOT NULL,
    visit_id BIGINT NOT NULL,
    date_kst DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), -- 9월 24일 수정: BaseEntity 표준
    updated_at TIMESTAMPTZ, -- 9월 24일 수정: BaseEntity 표준
    deleted_at TIMESTAMPTZ, -- 9월 24일 수정: BaseEntity 표준 (하지만 DailyLike는 실제로 사용하지 않음)
    CONSTRAINT fk_daily_likes_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_daily_likes_place FOREIGN KEY (place_id) REFERENCES places(place_id),
    CONSTRAINT fk_daily_likes_visit FOREIGN KEY (visit_id) REFERENCES visits(visit_id),
    CONSTRAINT chk_daily_like_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT uq_daily_like_visit UNIQUE (visit_id) -- 방문당 좋아요 1개 제한
    );

CREATE INDEX IF NOT EXISTS idx_daily_likes_user ON daily_likes (user_id);
CREATE INDEX IF NOT EXISTS idx_daily_likes_place ON daily_likes (place_id);
CREATE INDEX IF NOT EXISTS idx_daily_likes_visit ON daily_likes (visit_id);
CREATE INDEX IF NOT EXISTS idx_daily_likes_date_kst ON daily_likes (date_kst);
CREATE INDEX IF NOT EXISTS idx_daily_likes_status ON daily_likes (status);
CREATE INDEX IF NOT EXISTS idx_daily_likes_place_active ON daily_likes (place_id) WHERE status = 'ACTIVE';

-- Place Daily Stats (장소별 일일 통계)
-- 9월 24일 수정: BaseEntity 상속으로 표준 타임스탬프 적용
CREATE TABLE IF NOT EXISTS place_daily_stats (
                                                 date_kst DATE NOT NULL,
                                                 place_id BIGINT NOT NULL,
                                                 starts INTEGER NOT NULL DEFAULT 0,
                                                 arrives INTEGER NOT NULL DEFAULT 0,
                                                 reviews INTEGER NOT NULL DEFAULT 0,
                                                 likes INTEGER NOT NULL DEFAULT 0,
                                                 hourly_arrives JSONB NOT NULL DEFAULT '{}',
                                                 hourly_starts JSONB NOT NULL DEFAULT '{}',
                                                 peak_hour INTEGER,
                                                 last_aggregated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), -- 9월 24일 수정: BaseEntity 표준
    updated_at TIMESTAMPTZ, -- 9월 24일 수정: BaseEntity 표준
    deleted_at TIMESTAMPTZ, -- 9월 24일 수정: BaseEntity 표준 (하지만 통계 데이터는 실제로 삭제하지 않음)
    PRIMARY KEY (date_kst, place_id),
    CONSTRAINT fk_place_daily_stats_place FOREIGN KEY (place_id) REFERENCES places(place_id),
    CONSTRAINT chk_peak_hour CHECK (peak_hour IS NULL OR (peak_hour >= 0 AND peak_hour <= 23))
    );

CREATE INDEX IF NOT EXISTS idx_place_daily_stats_place ON place_daily_stats (place_id);
CREATE INDEX IF NOT EXISTS idx_place_daily_stats_date ON place_daily_stats (date_kst);
CREATE INDEX IF NOT EXISTS idx_place_daily_stats_aggregated ON place_daily_stats (last_aggregated_at);

-- Review Reports (신고 관리)
-- 9월 24일 수정: BaseEntity 상속으로 표준 타임스탬프 적용
CREATE TABLE IF NOT EXISTS review_reports (
                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL,
    reporter_id UUID NOT NULL,
    reason VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), -- 9월 24일 수정: BaseEntity 표준
    updated_at TIMESTAMPTZ, -- 9월 24일 수정: BaseEntity 표준 (하지만 실제로 사용하지 않음)
    deleted_at TIMESTAMPTZ, -- 9월 24일 수정: BaseEntity 표준 (하지만 실제로 사용하지 않음)
    CONSTRAINT fk_review_reports_review FOREIGN KEY (review_id) REFERENCES reviews(id),
    CONSTRAINT fk_review_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT chk_report_reason CHECK (reason IN ('SPAM', 'INAPPROPRIATE', 'FAKE', 'OFFENSIVE', 'OTHER')),
    CONSTRAINT uq_review_report_user UNIQUE (review_id, reporter_id) -- 중복 신고 방지
    );

CREATE INDEX IF NOT EXISTS idx_review_reports_review ON review_reports (review_id);
CREATE INDEX IF NOT EXISTS idx_review_reports_reporter ON review_reports (reporter_id);
CREATE INDEX IF NOT EXISTS idx_review_reports_reason ON review_reports (reason);
CREATE INDEX IF NOT EXISTS idx_review_reports_created_at ON review_reports (created_at);

-- 내 테이블들만 성능 최적화 인덱스
-- 팀장의 visits 테이블 인덱스는 추후 협의 후 추가 예정
-- 9월 24일 수정: BaseEntity 적용으로 인한 인덱스 최적화
CREATE INDEX IF NOT EXISTS idx_reviews_place_created_date ON reviews (place_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_daily_likes_place_date ON daily_likes (place_id, date_kst) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_review_reports_review_created ON review_reports (review_id, created_at);
