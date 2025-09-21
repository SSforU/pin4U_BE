# tools/convert_stations.py
import csv, sys, os, re
from pathlib import Path
from collections import defaultdict

BASE = Path(__file__).resolve().parent      # ==> pin4U_BE/tools
RAW  = str(BASE / 'raw_seoul_stations.csv')
OUT  = str(BASE / 'stations_for_pin4u.csv')
SQL  = str(BASE / 'stations_upsert.sql')

# 1) 다양한 인코딩 자동 시도
def smart_open(path):
    for enc in ('utf-8-sig', 'utf-8', 'cp949', 'euc-kr'):
        try:
            return open(path, 'r', encoding=enc, newline='')
        except UnicodeDecodeError:
            continue
    # 마지막 시도
    return open(path, 'r', encoding='utf-8', errors='replace', newline='')

# 2) 헤더 키 추출(한국어/영문 다양한 변형 허용)
def header_key(h):
    v = h.strip()
    v = re.sub(r'\s+', '', v)
    return v

# 3) 호선명 정규화
def norm_line(s):
    if s is None: return None
    s = str(s).strip()
    # 예: "9호선(연장)" → "9호선"
    s = re.sub(r'\(.*?\)', '', s)
    s = s.replace(' ', '')
    # "9" → "9호선"
    if re.fullmatch(r'\d+', s):
        s = f'{s}호선'
    return s

# 4) 라인번호 2자리 추출 (없는 경우 00)
def line_num2(line):
    m = re.search(r'(\d+)', line or '')
    if not m:
        return '00'
    return f'{int(m.group(1)):02d}'

# 5) 코드 강제 매핑(우리 프로젝트 시드와 일치)
FIXED = {
    ('숭실대입구','7호선'): 'S0701',
    ('서울대입구','2호선'): 'S0222',
}

def build_code_map(rows):
    # 라인별로 묶어 정렬 후 01부터 부여
    groups = defaultdict(list)
    for r in rows:
        groups[r['line']].append(r)
    code_map = {}

    for line, items in groups.items():
        # 정렬: (lng asc, lat desc, name asc) → 재실행 안정
        items_sorted = sorted(items, key=lambda x: (float(x['lng']), -float(x['lat']), x['name']))
        ln2 = line_num2(line)
        seq = 1
        for row in items_sorted:
            key = (row['name'], row['line'])
            if key in FIXED:
                code_map[key] = FIXED[key]
                continue
            # 2자리 순번 (01..99)
            code_map[key] = f"S{ln2}{seq:02d}"
            seq += 1
    return code_map

def main():
    if not os.path.exists(RAW):
        print(f"[ERROR] 입력 파일이 없습니다: {RAW}")
        sys.exit(1)

    f = smart_open(RAW)
    reader = csv.DictReader(f)
    headers = [header_key(h) for h in reader.fieldnames]

    # 컬럼 후보군 (여러 표기 대응)
    name_keys = {'역한글명칭','역명','전철역명','정류장명','stationname','name'}
    line_keys = {'호선명칭','노선명','호선','line'}
    x_keys    = {'환승역x좌표','x','lon','lng','경도'}
    y_keys    = {'환승역y좌표','y','lat','위도'}

    def pick(cols):
        for k in cols:
            for i, h in enumerate(headers):
                if k.lower() == h.lower():
                    return reader.fieldnames[i]
        return None

    col_name = pick(name_keys)
    col_line = pick(line_keys)
    col_x    = pick(x_keys)
    col_y    = pick(y_keys)

    if not all([col_name, col_line, col_x, col_y]):
        print("[ERROR] CSV 헤더 매핑 실패.")
        print("필요 헤더 예시: 역한글명칭 / 호선명칭 / 환승역X좌표 / 환승역Y좌표")
        sys.exit(2)

    # 1차 읽고 정규화
    rows = []
    for r in reader:
        name = (r.get(col_name) or '').strip()
        line = norm_line(r.get(col_line))
        try:
            lng = float(str(r.get(col_x)).strip())
            lat = float(str(r.get(col_y)).strip())
        except Exception:
            continue
        if not name or not line:
            continue
        rows.append({'name': name, 'line': line, 'lat': lat, 'lng': lng})

    # 코드 생성 (고정 매핑 우선)
    code_map = build_code_map(rows)

    # 출력 CSV
    with open(OUT, 'w', encoding='utf-8', newline='') as wf:
        w = csv.writer(wf)
        w.writerow(['code','name','line','lat','lng'])
        seen = set()
        for row in rows:
            key = (row['name'], row['line'])
            code = code_map[key]
            # 중복 방지
            if code in seen:
                # (아주 예외적으로) 겹치면 뒤에 'A','B' 붙이기
                suffix = ord('A')
                base = code
                while code in seen:
                    code = f"{base}{chr(suffix)}"
                    suffix += 1
            seen.add(code)
            w.writerow([code, row['name'], row['line'], f"{row['lat']:.7f}", f"{row['lng']:.7f}"])

    # psql 업서트 스크립트도 만들어둠
    with open(SQL, 'w', encoding='utf-8') as wf:
        wf.write("""BEGIN;

DROP TABLE IF EXISTS temp_stations;
CREATE TEMP TABLE temp_stations (
  code varchar(16),
  name varchar(100),
  line varchar(50),
  lat numeric(11,7),
  lng numeric(11,7)
);

\\copy temp_stations FROM 'tools/stations_for_pin4u.csv' CSV HEADER;

INSERT INTO stations(code,name,line,lat,lng)
SELECT code,name,line,lat,lng FROM temp_stations
ON CONFLICT (code) DO UPDATE
SET name=EXCLUDED.name,
    line=EXCLUDED.line,
    lat =EXCLUDED.lat,
    lng =EXCLUDED.lng;

COMMIT;
""")

    print(f"[OK] 변환 완료 → {OUT}")
    print(f"[OK] 업서트 SQL → {SQL}")
    print("psql에서: \\i tools/stations_upsert.sql 또는 내용 붙여넣기")

if __name__ == '__main__':
    main()
