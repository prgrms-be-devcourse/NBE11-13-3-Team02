-- 같이사 프로젝트 개발용 시드 데이터
-- ddl-auto: create-drop + sql.init.mode: always 일 때만 매 기동마다 실행하세요.
-- ddl-auto: update 로 데이터를 유지할 때는 sql.init.mode: never 로 두세요.
-- (update + always 조합은 users.email 등 unique 컬럼에서 Duplicate entry 로 기동이 실패합니다.)

-- 비밀번호는 전부 '1234' (BCryptPasswordEncoder로 해시한 값)
-- provider를 명시 안 하면 MySQL이 NOT NULL enum 컬럼에 임의의 기본값(정의 순서상 첫 값)을 넣어버려서 반드시 명시해야 함
INSERT INTO users (email, password, name, role, provider, created_at) VALUES
                                                                 ('buyer1@test.com', '$2a$10$WtWTKE7oj11ctlGs9G5wNuLSBbSapVXma07TWX3Qf9ZcVcTye4N1y', '구매자1', 'ROLE_BUYER', 'LOCAL', NOW()),
                                                                 ('buyer2@test.com', '$2a$10$WtWTKE7oj11ctlGs9G5wNuLSBbSapVXma07TWX3Qf9ZcVcTye4N1y', '구매자2', 'ROLE_BUYER', 'LOCAL', NOW()),
                                                                 ('seller1@test.com', '$2a$10$WtWTKE7oj11ctlGs9G5wNuLSBbSapVXma07TWX3Qf9ZcVcTye4N1y', '판매자1', 'ROLE_SELLER', 'LOCAL', NOW()),
                                                                 ('admin@test.com',  '$2a$10$WtWTKE7oj11ctlGs9G5wNuLSBbSapVXma07TWX3Qf9ZcVcTye4N1y', '관리자',  'ROLE_ADMIN', 'LOCAL', NOW());

INSERT INTO category (name, parent_id) VALUES
                                           ('생활/리빙', NULL),
                                           ('식품', NULL),
                                           ('디지털', NULL),
                                           ('기타', NULL);

-- 하위 카테고리 추가
-- MySQL은 INSERT ... VALUES의 서브쿼리에서 같은 테이블(category)을 참조할 수 없어서
-- ("You can't specify target table 'category' for update in FROM clause") 상위 카테고리의
-- id를 직접 리터럴로 넣는다. 위 INSERT에서 생활/리빙=1, 식품=2, 디지털=3, 기타=4로 고정 생성됨.
INSERT INTO category (name, parent_id) VALUES
  ('주방', 1),
  ('침실/욕실', 1),
  ('커피/차', 2),
  ('간편식', 2),
  ('액세서리', 3),
  ('모바일', 3),
  ('캠핑/아웃도어', 4),
  ('반려동물용품', 4);

INSERT INTO product (seller_id, category_id, name, description, base_price, stock, status, created_at) VALUES
(3, 1, '텀블러 6종 세트', '보온·보냉 겸용 텀블러 6종 구성', 18000, 100, 'ON_SALE', NOW()),
(3, 2, '유기농 원두 1kg', '싱글 오리진 원두', 16000, 100, 'ON_SALE', NOW()),
(3, (SELECT id FROM category WHERE name='주방' LIMIT 1), '스텐 주방용품 4종', '스텐 소재 주방용품 4종 세트', 22000, 60, 'ON_SALE', NOW()),
(3, (SELECT id FROM category WHERE name='침실/욕실' LIMIT 1), '극세사 베개커버', '극세사 소재 베개커버', 8000, 40, 'ON_SALE', NOW()),
(3, (SELECT id FROM category WHERE name='커피/차' LIMIT 1), '스페셜티 핸드드립 원두 200g', '소량 로스팅 스페셜티 원두', 12000, 120, 'ON_SALE', NOW()),
(3, (SELECT id FROM category WHERE name='간편식' LIMIT 1), '즉석 국/찌개 3종 세트', '간편 조리 식품 세트', 15000, 80, 'ON_SALE', NOW()),
(3, (SELECT id FROM category WHERE name='액세서리' LIMIT 1), '무선 이어폰 케이스', '보호용 실리콘 케이스', 5000, 150, 'ON_SALE', NOW()),
(3, (SELECT id FROM category WHERE name='모바일' LIMIT 1), '휴대용 보조 배터리 10000mAh', '슬림형 보조배터리', 22000, 200, 'ON_SALE', NOW()),
(3, (SELECT id FROM category WHERE name='캠핑/아웃도어' LIMIT 1), '캠핑용 LED 랜턴', '충전식 LED 랜턴', 18000, 70, 'ON_SALE', NOW()),
(3, (SELECT id FROM category WHERE name='반려동물용품' LIMIT 1), '고양이 간식 믹스', '수제 고양이 간식', 7000, 90, 'ON_SALE', NOW());

-- 상품 목록/검색/페이지네이션 테스트용 대량 시드 데이터 (product id 3~100, 총 100개 상품)
-- 제일 저렴한 상품(마지막 행, '스마트워치 (리필)')이 10원
INSERT INTO product (seller_id, category_id, name, description, base_price, stock, status, created_at) VALUES
(3, 1, '텀블러', '텀블러 상세 설명입니다.', 39000, 20, 'ON_SALE', NOW()),
(3, 1, '접이식 우산', '접이식 우산 상세 설명입니다.', 1000, 80, 'ON_SALE', NOW()),
(3, 1, '극세사 수건 세트', '극세사 수건 세트 상세 설명입니다.', 5000, 50, 'ON_SALE', NOW()),
(3, 1, '무드 스탠드 조명', '무드 스탠드 조명 상세 설명입니다.', 3000, 20, 'ON_SALE', NOW()),
(3, 1, '다용도 정리함', '다용도 정리함 상세 설명입니다.', 45000, 20, 'ON_SALE', NOW()),
(3, 1, '극세사 담요', '극세사 담요 상세 설명입니다.', 29000, 150, 'ON_SALE', NOW()),
(3, 1, '실리콘 주방장갑', '실리콘 주방장갑 상세 설명입니다.', 1500, 10, 'ON_SALE', NOW()),
(3, 1, '접이식 빨래건조대', '접이식 빨래건조대 상세 설명입니다.', 2000, 50, 'ON_SALE', NOW()),
(3, 1, '투명 수납박스', '투명 수납박스 상세 설명입니다.', 5000, 10, 'ON_SALE', NOW()),
(3, 1, '극세사 슬리퍼', '극세사 슬리퍼 상세 설명입니다.', 25000, 50, 'ON_SALE', NOW()),
(3, 1, '욕실 방수매트', '욕실 방수매트 상세 설명입니다.', 59000, 150, 'ON_SALE', NOW()),
(3, 1, '다림질 보드', '다림질 보드 상세 설명입니다.', 5000, 200, 'ON_SALE', NOW()),
(3, 1, '행거형 옷걸이 세트', '행거형 옷걸이 세트 상세 설명입니다.', 29000, 80, 'ON_SALE', NOW()),
(3, 1, '미니 청소기', '미니 청소기 상세 설명입니다.', 1000, 30, 'ON_SALE', NOW()),
(3, 1, '탈취제 세트', '탈취제 세트 상세 설명입니다.', 59000, 150, 'ON_SALE', NOW()),
(3, 2, '원두커피 1kg', '원두커피 1kg 상세 설명입니다.', 8900, 80, 'ON_SALE', NOW()),
(3, 2, '견과류 모듬 세트', '견과류 모듬 세트 상세 설명입니다.', 3000, 50, 'ON_SALE', NOW()),
(3, 2, '유기농 아카시아꿀', '유기농 아카시아꿀 상세 설명입니다.', 8900, 20, 'ON_SALE', NOW()),
(3, 2, '수제 딸기잼', '수제 딸기잼 상세 설명입니다.', 2000, 150, 'ON_SALE', NOW()),
(3, 2, '곡물 그래놀라', '곡물 그래놀라 상세 설명입니다.', 2500, 100, 'ON_SALE', NOW()),
(3, 2, '건조 과일칩', '건조 과일칩 상세 설명입니다.', 9900, 80, 'ON_SALE', NOW()),
(3, 2, '전통 된장 세트', '전통 된장 세트 상세 설명입니다.', 1500, 200, 'ON_SALE', NOW()),
(3, 2, '냉동 손만두', '냉동 손만두 상세 설명입니다.', 25000, 20, 'ON_SALE', NOW()),
(3, 2, '프리미엄 재래김', '프리미엄 재래김 상세 설명입니다.', 12000, 20, 'ON_SALE', NOW()),
(3, 2, '유기농 올리브유', '유기농 올리브유 상세 설명입니다.', 25000, 80, 'ON_SALE', NOW()),
(3, 2, '제철 과일 박스', '제철 과일 박스 상세 설명입니다.', 39000, 100, 'ON_SALE', NOW()),
(3, 2, '수제 쿠키 세트', '수제 쿠키 세트 상세 설명입니다.', 29000, 50, 'ON_SALE', NOW()),
(3, 2, '핸드드립 원두 샘플러', '핸드드립 원두 샘플러 상세 설명입니다.', 59000, 20, 'ON_SALE', NOW()),
(3, 2, '국내산 잡곡 세트', '국내산 잡곡 세트 상세 설명입니다.', 1500, 50, 'ON_SALE', NOW()),
(3, 2, '유자차 세트', '유자차 세트 상세 설명입니다.', 7500, 20, 'ON_SALE', NOW()),
(3, 3, '무선 이어폰', '무선 이어폰 상세 설명입니다.', 5000, 20, 'ON_SALE', NOW()),
(3, 3, '보조배터리 20000mAh', '보조배터리 20000mAh 상세 설명입니다.', 12000, 80, 'ON_SALE', NOW()),
(3, 3, '블루투스 스피커', '블루투스 스피커 상세 설명입니다.', 16900, 100, 'ON_SALE', NOW()),
(3, 3, 'USB-C 허브', 'USB-C 허브 상세 설명입니다.', 3900, 100, 'ON_SALE', NOW()),
(3, 3, '무선 마우스', '무선 마우스 상세 설명입니다.', 9900, 50, 'ON_SALE', NOW()),
(3, 3, '기계식 키보드', '기계식 키보드 상세 설명입니다.', 45000, 80, 'ON_SALE', NOW()),
(3, 3, '웹캠 1080p', '웹캠 1080p 상세 설명입니다.', 59000, 20, 'ON_SALE', NOW()),
(3, 3, '스마트워치', '스마트워치 상세 설명입니다.', 35000, 30, 'ON_SALE', NOW()),
(3, 3, '케이블 정리함', '케이블 정리함 상세 설명입니다.', 25000, 50, 'ON_SALE', NOW()),
(3, 3, '노트북 거치대', '노트북 거치대 상세 설명입니다.', 3900, 200, 'ON_SALE', NOW()),
(3, 3, '무선 충전패드', '무선 충전패드 상세 설명입니다.', 12000, 80, 'ON_SALE', NOW()),
(3, 3, '미니 스탠드 선풍기', '미니 스탠드 선풍기 상세 설명입니다.', 39000, 50, 'ON_SALE', NOW()),
(3, 3, '휴대용 모니터', '휴대용 모니터 상세 설명입니다.', 45000, 100, 'ON_SALE', NOW()),
(3, 3, '블루투스 키보드', '블루투스 키보드 상세 설명입니다.', 1500, 50, 'ON_SALE', NOW()),
(3, 3, '게이밍 헤드셋', '게이밍 헤드셋 상세 설명입니다.', 1500, 100, 'ON_SALE', NOW()),
(3, 4, '반려동물 간식 세트', '반려동물 간식 세트 상세 설명입니다.', 12000, 80, 'ON_SALE', NOW()),
(3, 4, '경량 캠핑 의자', '경량 캠핑 의자 상세 설명입니다.', 2000, 50, 'ON_SALE', NOW()),
(3, 4, '카본 등산 스틱', '카본 등산 스틱 상세 설명입니다.', 29000, 100, 'ON_SALE', NOW()),
(3, 4, '요가매트', '요가매트 상세 설명입니다.', 4500, 200, 'ON_SALE', NOW()),
(3, 4, '강아지 장난감 세트', '강아지 장난감 세트 상세 설명입니다.', 12000, 200, 'ON_SALE', NOW()),
(3, 4, '미니 텐트', '미니 텐트 상세 설명입니다.', 3000, 80, 'ON_SALE', NOW()),
(3, 4, '캠핑 랜턴', '캠핑 랜턴 상세 설명입니다.', 3000, 50, 'ON_SALE', NOW()),
(3, 4, '휴대용 선풍기', '휴대용 선풍기 상세 설명입니다.', 68000, 80, 'ON_SALE', NOW()),
(3, 4, '미니 화분 세트', '미니 화분 세트 상세 설명입니다.', 68000, 150, 'ON_SALE', NOW()),
(3, 4, '가죽 다이어리', '가죽 다이어리 상세 설명입니다.', 29000, 150, 'ON_SALE', NOW()),
(3, 4, '캠핑 테이블', '캠핑 테이블 상세 설명입니다.', 9900, 50, 'ON_SALE', NOW()),
(3, 4, '휴대용 돗자리', '휴대용 돗자리 상세 설명입니다.', 3000, 200, 'ON_SALE', NOW()),
(3, 4, '등산 배낭', '등산 배낭 상세 설명입니다.', 2000, 10, 'ON_SALE', NOW()),
(3, 4, '고양이 스크래처', '고양이 스크래처 상세 설명입니다.', 2500, 30, 'ON_SALE', NOW()),
(3, 4, '야외용 방석', '야외용 방석 상세 설명입니다.', 39000, 30, 'ON_SALE', NOW()),
(3, 1, '텀블러 (리필)', '텀블러 (리필) 상세 설명입니다.', 45000, 150, 'ON_SALE', NOW()),
(3, 1, '접이식 우산 (리필)', '접이식 우산 (리필) 상세 설명입니다.', 35000, 20, 'ON_SALE', NOW()),
(3, 1, '극세사 수건 세트 (리필)', '극세사 수건 세트 (리필) 상세 설명입니다.', 12000, 150, 'ON_SALE', NOW()),
(3, 1, '무드 스탠드 조명 (리필)', '무드 스탠드 조명 (리필) 상세 설명입니다.', 35000, 200, 'ON_SALE', NOW()),
(3, 1, '다용도 정리함 (리필)', '다용도 정리함 (리필) 상세 설명입니다.', 22000, 80, 'ON_SALE', NOW()),
(3, 1, '극세사 담요 (리필)', '극세사 담요 (리필) 상세 설명입니다.', 25000, 10, 'ON_SALE', NOW()),
(3, 1, '실리콘 주방장갑 (리필)', '실리콘 주방장갑 (리필) 상세 설명입니다.', 45000, 20, 'ON_SALE', NOW()),
(3, 1, '접이식 빨래건조대 (리필)', '접이식 빨래건조대 (리필) 상세 설명입니다.', 45000, 80, 'ON_SALE', NOW()),
(3, 1, '투명 수납박스 (리필)', '투명 수납박스 (리필) 상세 설명입니다.', 39000, 100, 'ON_SALE', NOW()),
(3, 1, '극세사 슬리퍼 (리필)', '극세사 슬리퍼 (리필) 상세 설명입니다.', 2500, 80, 'ON_SALE', NOW()),
(3, 1, '욕실 방수매트 (리필)', '욕실 방수매트 (리필) 상세 설명입니다.', 15000, 30, 'ON_SALE', NOW()),
(3, 1, '다림질 보드 (리필)', '다림질 보드 (리필) 상세 설명입니다.', 16900, 10, 'ON_SALE', NOW()),
(3, 1, '행거형 옷걸이 세트 (리필)', '행거형 옷걸이 세트 (리필) 상세 설명입니다.', 68000, 80, 'ON_SALE', NOW()),
(3, 1, '미니 청소기 (리필)', '미니 청소기 (리필) 상세 설명입니다.', 22000, 30, 'ON_SALE', NOW()),
(3, 1, '탈취제 세트 (리필)', '탈취제 세트 (리필) 상세 설명입니다.', 22000, 20, 'ON_SALE', NOW()),
(3, 2, '원두커피 1kg (리필)', '원두커피 1kg (리필) 상세 설명입니다.', 39000, 80, 'ON_SALE', NOW()),
(3, 2, '견과류 모듬 세트 (리필)', '견과류 모듬 세트 (리필) 상세 설명입니다.', 39000, 50, 'ON_SALE', NOW()),
(3, 2, '유기농 아카시아꿀 (리필)', '유기농 아카시아꿀 (리필) 상세 설명입니다.', 3000, 100, 'ON_SALE', NOW()),
(3, 2, '수제 딸기잼 (리필)', '수제 딸기잼 (리필) 상세 설명입니다.', 3900, 10, 'ON_SALE', NOW()),
(3, 2, '곡물 그래놀라 (리필)', '곡물 그래놀라 (리필) 상세 설명입니다.', 35000, 100, 'ON_SALE', NOW()),
(3, 2, '건조 과일칩 (리필)', '건조 과일칩 (리필) 상세 설명입니다.', 19900, 10, 'ON_SALE', NOW()),
(3, 2, '전통 된장 세트 (리필)', '전통 된장 세트 (리필) 상세 설명입니다.', 2500, 100, 'ON_SALE', NOW()),
(3, 2, '냉동 손만두 (리필)', '냉동 손만두 (리필) 상세 설명입니다.', 7500, 50, 'ON_SALE', NOW()),
(3, 2, '프리미엄 재래김 (리필)', '프리미엄 재래김 (리필) 상세 설명입니다.', 1500, 50, 'ON_SALE', NOW()),
(3, 2, '유기농 올리브유 (리필)', '유기농 올리브유 (리필) 상세 설명입니다.', 29000, 20, 'ON_SALE', NOW()),
(3, 2, '제철 과일 박스 (리필)', '제철 과일 박스 (리필) 상세 설명입니다.', 2000, 200, 'ON_SALE', NOW()),
(3, 2, '수제 쿠키 세트 (리필)', '수제 쿠키 세트 (리필) 상세 설명입니다.', 2000, 30, 'ON_SALE', NOW()),
(3, 2, '핸드드립 원두 샘플러 (리필)', '핸드드립 원두 샘플러 (리필) 상세 설명입니다.', 3000, 200, 'ON_SALE', NOW()),
(3, 2, '국내산 잡곡 세트 (리필)', '국내산 잡곡 세트 (리필) 상세 설명입니다.', 25000, 30, 'ON_SALE', NOW()),
(3, 2, '유자차 세트 (리필)', '유자차 세트 (리필) 상세 설명입니다.', 6900, 150, 'ON_SALE', NOW()),
(3, 3, '무선 이어폰 (리필)', '무선 이어폰 (리필) 상세 설명입니다.', 4500, 50, 'ON_SALE', NOW()),
(3, 3, '보조배터리 20000mAh (리필)', '보조배터리 20000mAh (리필) 상세 설명입니다.', 59000, 80, 'ON_SALE', NOW()),
(3, 3, '블루투스 스피커 (리필)', '블루투스 스피커 (리필) 상세 설명입니다.', 12000, 100, 'ON_SALE', NOW()),
(3, 3, 'USB-C 허브 (리필)', 'USB-C 허브 (리필) 상세 설명입니다.', 16900, 200, 'ON_SALE', NOW()),
(3, 3, '무선 마우스 (리필)', '무선 마우스 (리필) 상세 설명입니다.', 2500, 50, 'ON_SALE', NOW()),
(3, 3, '기계식 키보드 (리필)', '기계식 키보드 (리필) 상세 설명입니다.', 5000, 20, 'ON_SALE', NOW()),
(3, 3, '웹캠 1080p (리필)', '웹캠 1080p (리필) 상세 설명입니다.', 8900, 10, 'ON_SALE', NOW()),
(3, 3, '스마트워치 (리필)', '스마트워치 (리필) 상세 설명입니다.', 10, 50, 'ON_SALE', NOW());

-- ============================================================
-- 공동구매(group_buy)
-- ============================================================

-- [시연용] 마감 인원이 딱 1명 남은 공동구매. group_buy id 1~3으로 먼저 생성해서
-- 시연 중 바로 참여 → "마감 임박/정원 마감"으로 전환되는 걸 즉시 보여줄 수 있도록 한다.
-- 마감시각(deadline)은 시연 준비 시간을 감안해 오늘 안에서 여유 있게 잡았다.
INSERT INTO group_buy (product_id, target_count, current_count, discount_rate, open_at, deadline, status, seller_id) VALUES
(1, 5, 4, 0.30, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 6 HOUR), 'RECRUITING', 3),
(2, 6, 5, 0.20, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 12 HOUR), 'RECRUITING', 3),
(3, 4, 3, 0.25, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3);

-- 공동구매 목록/검색/페이지네이션 테스트용 대량 시드 데이터 (상품 1~100 전부 대상, 총 100개)
INSERT INTO group_buy (product_id, target_count, current_count, discount_rate, open_at, deadline, status, seller_id) VALUES
(1, 10, 8, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(2, 8,  2, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(3, 20, 4, 0.35, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(4, 5, 0, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(5, 20, 18, 0.05, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(6, 12, 0, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(7, 25, 2, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(8, 40, 27, 0.05, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(9, 50, 7, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(10, 50, 3, 0.50, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(11, 25, 1, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(12, 40, 8, 0.25, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(13, 10, 8, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(14, 15, 8, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(15, 50, 36, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(16, 8, 1, 0.50, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(17, 50, 13, 0.40, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(18, 40, 27, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(19, 50, 29, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(20, 12, 2, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(21, 50, 19, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(22, 20, 14, 0.25, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(23, 8, 1, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(24, 10, 5, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(25, 25, 1, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(26, 40, 36, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(27, 20, 19, 0.40, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(28, 30, 2, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(29, 30, 22, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(30, 15, 10, 0.50, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(31, 30, 9, 0.35, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(32, 20, 0, 0.40, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(33, 10, 9, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(34, 5, 1, 0.25, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(35, 12, 6, 0.35, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(36, 30, 2, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(37, 25, 17, 0.25, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(38, 25, 17, 0.25, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(39, 25, 11, 0.35, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(40, 10, 1, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(41, 12, 10, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(42, 30, 26, 0.50, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(43, 15, 4, 0.05, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(44, 25, 17, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(45, 50, 20, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(46, 40, 39, 0.05, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(47, 40, 25, 0.35, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(48, 25, 3, 0.40, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(49, 25, 1, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(50, 12, 7, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(51, 20, 19, 0.05, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(52, 5, 4, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(53, 8, 5, 0.50, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(54, 8, 3, 0.50, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(55, 10, 4, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(56, 20, 15, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(57, 30, 14, 0.40, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(58, 15, 1, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(59, 20, 8, 0.40, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(60, 10, 8, 0.05, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(61, 40, 23, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(62, 40, 1, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(63, 8, 4, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(64, 10, 5, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(65, 40, 32, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(66, 12, 9, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(67, 12, 6, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(68, 40, 31, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(69, 5, 0, 0.25, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(70, 15, 3, 0.50, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(71, 30, 25, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(72, 8, 3, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(73, 30, 6, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(74, 30, 19, 0.50, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(75, 5, 3, 0.30, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(76, 8, 1, 0.35, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(77, 12, 7, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(78, 20, 2, 0.35, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(79, 25, 23, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(80, 10, 2, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 'RECRUITING', 3),
(81, 10, 9, 0.40, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(82, 10, 9, 0.50, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(83, 20, 4, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(84, 10, 0, 0.05, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(85, 8, 2, 0.35, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(86, 12, 3, 0.05, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(87, 12, 4, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(88, 50, 20, 0.25, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(89, 25, 4, 0.05, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
(90, 20, 14, 0.50, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'RECRUITING', 3),
(91, 40, 26, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(92, 40, 9, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(93, 5, 3, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(94, 5, 1, 0.15, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
(95, 30, 19, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(96, 5, 2, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(97, 40, 30, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
(98, 5, 1, 0.20, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
(99, 5, 0, 0.45, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
(100, 40, 1, 0.10, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3);

-- [시연용] 구매자별 "내 참여내역 / 내 주문" 화면에서 다양한 상태를 보여주기 위한 전용 공동구매.
-- 위의 대량 시드(100개) 뒤에 이어지므로 id는 104~115가 된다.
-- product_id 4~15(생활/리빙 카테고리)를 하나씩 재사용해 각기 다른 결과를 시연한다.
INSERT INTO group_buy (product_id, target_count, current_count, discount_rate, open_at, deadline, status, seller_id) VALUES
-- id 104: buyer1 참여중(결제 전) 시연용, 모집중
(5, 10, 2, 0.10, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY), 'RECRUITING', 3),
-- id 105: buyer1 참여취소 시연용, 모집중
(6, 8, 0, 0.15, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), 'RECRUITING', 3),
-- id 106: buyer1 결제완료(주문: 공동구매 결과 대기중) 시연용, 모집중
(8, 15, 1, 0.10, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RECRUITING', 3),
-- id 107: buyer1 결제완료 + 목표 달성 + 배송지 미입력(주문: 상품준비중) 시연용
(9, 3, 3, 0.20, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 'SETTLED', 3),
-- id 108: buyer1 결제완료 + 목표 달성 + 배송지 입력 + 배송중 시연용
(10, 2, 2, 0.30, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 'SETTLED', 3),
-- id 109: buyer1 목표 미달로 환불 + 주문취소 시연용
(11, 20, 5, 0.15, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 'SETTLED', 3),
-- id 110: buyer2 참여중(결제 전) 시연용, 모집중
(12, 10, 3, 0.20, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY), 'RECRUITING', 3),
-- id 111: buyer2 결제완료(주문: 공동구매 결과 대기중) 시연용, 모집중
(13, 20, 9, 0.15, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), 'RECRUITING', 3),
-- id 112: buyer2 결제완료 + 목표 달성 + 배송지 입력(주문: 상품준비중) 시연용
(14, 4, 4, 0.25, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 'SETTLED', 3),
-- id 113: buyer2 결제완료 + 목표 달성 + 배송완료 시연용
(15, 1, 1, 0.10, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), 'SETTLED', 3),
-- id 114: buyer2 배송완료 후 반품 처리완료(주문: 반품완료) 시연용
(4, 5, 5, 0.05, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), 'SETTLED', 3),
-- id 115: buyer2 배송완료 후 반품 진행중(주문: 반품중) 시연용
(7, 1, 1, 0.20, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 'SETTLED', 3);

-- ============================================================
-- 참여(participation) — buyer1(id 1), buyer2(id 2)의 다양한 상태
-- ============================================================
INSERT INTO participation (group_buy_id, user_id, quantity, status, participated_at) VALUES
-- participation 1: buyer1, 참여만 하고 결제는 하지 않은 상태
(104, 1, 2, 'PARTICIPATING', DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- participation 2: buyer1, 참여 후 스스로 취소한 상태
(105, 1, 1, 'CANCELLED', DATE_SUB(NOW(), INTERVAL 2 DAY)),
-- participation 3: buyer1, 결제완료 + 공동구매 결과 대기중
(106, 1, 1, 'CONFIRMED', DATE_SUB(NOW(), INTERVAL 4 DAY)),
-- participation 4: buyer1, 결제완료 + 목표달성, 배송지 미입력(상품준비중)
(107, 1, 3, 'CONFIRMED', DATE_SUB(NOW(), INTERVAL 9 DAY)),
-- participation 5: buyer1, 결제완료 + 목표달성 + 배송중
(108, 1, 2, 'CONFIRMED', DATE_SUB(NOW(), INTERVAL 14 DAY)),
-- participation 6: buyer1, 목표미달로 환불된 상태
(109, 1, 1, 'REFUNDED', DATE_SUB(NOW(), INTERVAL 7 DAY)),
-- participation 7: buyer2, 참여만 하고 결제는 하지 않은 상태
(110, 2, 1, 'PARTICIPATING', DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- participation 8: buyer2, 결제완료 + 공동구매 결과 대기중
(111, 2, 1, 'CONFIRMED', DATE_SUB(NOW(), INTERVAL 5 DAY)),
-- participation 9: buyer2, 결제완료 + 목표달성 + 배송지 입력(상품준비중)
(112, 2, 4, 'CONFIRMED', DATE_SUB(NOW(), INTERVAL 8 DAY)),
-- participation 10: buyer2, 결제완료 + 목표달성 + 배송완료
(113, 2, 1, 'CONFIRMED', DATE_SUB(NOW(), INTERVAL 19 DAY)),
-- participation 11: buyer2, 배송완료 후 반품 처리완료(환불)
(114, 2, 5, 'REFUNDED', DATE_SUB(NOW(), INTERVAL 24 DAY)),
-- participation 12: buyer2, 배송완료 후 반품 진행중(환불)
(115, 2, 1, 'REFUNDED', DATE_SUB(NOW(), INTERVAL 29 DAY));

-- ============================================================
-- 결제(payment) — 위 participation 3~6, 8~12에 대응 (1~2, 7번은 결제 전이라 없음)
-- ============================================================
INSERT INTO payment (participation_id, amount, status, created_at, updated_at, paid_at, refunded_at) VALUES
-- payment 1 (participation 3): 결제완료, 공동구매 결과 대기중
(3, 26100, 'PAID', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL),
-- payment 2 (participation 4): 결제완료, 배송지 미입력 상태로 상품준비중
(4, 3600, 'PAID', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), NULL),
-- payment 3 (participation 5): 결제완료, 배송중
(5, 2800, 'PAID', DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY), NULL),
-- payment 4 (participation 6): 목표미달로 환불됨
(6, 4250, 'REFUNDED', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- payment 5 (participation 8): 결제완료, 공동구매 결과 대기중
(8, 50150, 'PAID', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL),
-- payment 6 (participation 9): 결제완료, 배송지 입력 후 상품준비중
(9, 15000, 'PAID', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), NULL),
-- payment 7 (participation 10): 결제완료, 배송완료
(10, 26100, 'PAID', DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY), NULL),
-- payment 8 (participation 11): 배송완료 후 반품 처리완료로 환불됨
(11, 4750, 'REFUNDED', DATE_SUB(NOW(), INTERVAL 24 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 24 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),
-- payment 9 (participation 12): 배송완료 후 반품 진행중, 환불은 이미 처리됨
(12, 36000, 'REFUNDED', DATE_SUB(NOW(), INTERVAL 29 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 29 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY));

-- ============================================================
-- 환불(refund) — payment 4, 8, 9에 대응
-- ============================================================
INSERT INTO refund (payment_id, amount, reason, status, pg_idempotency_key, pg_cancellation_transaction_id,
                     retry_count, requested_at, refunded_at, updated_at) VALUES
(4, 4250, '공동구매 목표 인원 미달', 'REFUNDED', 'seed-refund-idem-0001', 'seed-refund-txn-0001',
 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(8, 4750, '단순 변심으로 인한 반품 요청', 'REFUNDED', 'seed-refund-idem-0002', 'seed-refund-txn-0002',
 0, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
(9, 36000, '단순 변심으로 인한 반품 요청', 'REFUNDED', 'seed-refund-idem-0003', 'seed-refund-txn-0003',
 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY));

-- ============================================================
-- 주문(order_table) — payment 1~9에 각각 1:1 대응
-- 배송지(recipient_*, address 등)는 PREPARING 이후 상태로 넘어간 주문에만 입력되어 있고,
-- SHIPPING/DELIVERED/RETURNING/RETURNED로 전이하려면 배송지가 반드시 등록되어 있어야 한다는
-- 도메인 규칙(Order.changeDeliveryStatusByAdmin)에 맞춰 데이터를 구성했다.
-- ============================================================
INSERT INTO order_table
(order_number, participation_id, payment_id, buyer_id, group_buy_id, product_id, product_name,
 quantity, base_price, discount_rate, discount_amount, amount,
 delivery_status, recipient_name, recipient_phone, zip_code, address, address_detail, delivery_request,
 preparation_started_at, shipping_started_at, delivered_at, created_at, updated_at) VALUES
-- order 1 (payment 1): 공동구매 결과 대기중 → 배송지 입력 전이라도 정상 상태
('900000001', 3, 1, 1, 106, 8, '극세사 담요',
 1, 29000, 0.10, 2900, 26100,
 'WAITING_FOR_GROUP_BUY', NULL, NULL, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
-- order 2 (payment 2): 목표 달성, 배송지 미입력 → 상품준비중에 머물러 있음(정상)
('900000002', 4, 2, 1, 107, 9, '실리콘 주방장갑',
 3, 1500, 0.20, 900, 3600,
 'PREPARING', NULL, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
-- order 3 (payment 3): 목표 달성, 배송지 입력 완료 후 배송중
('900000003', 5, 3, 1, 108, 10, '접이식 빨래건조대',
 2, 2000, 0.30, 1200, 2800,
 'SHIPPING', '구매자1', '010-1111-2222', '06236', '서울특별시 강남구 테헤란로 123', '101동 1001호', '부재 시 경비실에 맡겨주세요',
 DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
-- order 4 (payment 4): 목표 미달로 환불 → 주문은 취소 처리
('900000004', 6, 4, 1, 109, 11, '투명 수납박스',
 1, 5000, 0.15, 750, 4250,
 'CANCELLED', NULL, NULL, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- order 5 (payment 5): 공동구매 결과 대기중
('900000005', 8, 5, 2, 111, 13, '욕실 방수매트',
 1, 59000, 0.15, 8850, 50150,
 'WAITING_FOR_GROUP_BUY', NULL, NULL, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
-- order 6 (payment 6): 목표 달성, 배송지 입력 완료 후 상품준비중(곧 발송 예정)
('900000006', 9, 6, 2, 112, 14, '다림질 보드',
 4, 5000, 0.25, 5000, 15000,
 'PREPARING', '구매자2', '010-3333-4444', '03181', '서울특별시 종로구 세종대로 100', '202동 2002호', '문 앞에 놔주세요',
 DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- order 7 (payment 7): 배송완료
('900000007', 10, 7, 2, 113, 15, '행거형 옷걸이 세트',
 1, 29000, 0.10, 2900, 26100,
 'DELIVERED', '구매자2', '010-3333-4444', '03181', '서울특별시 종로구 세종대로 100', '202동 2002호', '문 앞에 놔주세요',
 DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY)),
-- order 8 (payment 8): 배송완료 후 반품 처리까지 완료
('900000008', 11, 8, 2, 114, 4, '접이식 우산',
 5, 1000, 0.05, 250, 4750,
 'RETURNED', '구매자2', '010-3333-4444', '03181', '서울특별시 종로구 세종대로 100', '202동 2002호', '문 앞에 놔주세요',
 DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_SUB(NOW(), INTERVAL 17 DAY), DATE_SUB(NOW(), INTERVAL 24 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
-- order 9 (payment 9): 배송완료 후 반품 진행중(아직 처리완료 전)
('900000009', 12, 9, 2, 115, 7, '다용도 정리함',
 1, 45000, 0.20, 9000, 36000,
 'RETURNING', '구매자2', '010-3333-4444', '03181', '서울특별시 종로구 세종대로 100', '202동 2002호', '문 앞에 놔주세요',
 DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 24 DAY), DATE_SUB(NOW(), INTERVAL 22 DAY), DATE_SUB(NOW(), INTERVAL 29 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY));
