#!/usr/bin/env bash
set -euo pipefail

# ===== 고정 설정 (KAKAO 직접 호출 없음) =====
BASE="${BASE:-http://localhost:8080}"
STATION="${STATION:-S0701}"           # 숭실대입구
OUT="${OUT:-src/main/resources/db/migration/R__mock_more_places.sql}"

# ===== 데이터: 장소명 + 이미지 배열 =====
read -r -d '' DATA <<'JSON'
[
  {"name":"샤브로21 숭실대","images":[
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20250418_35%2F17449692155573E1Au_JPEG%2F3.jpg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20250418_209%2F1744969215673qp6ES_JPEG%2F%25BB%25FE%25BA%25EA%25B7%25CE21_%25BF%25C0%25C7%25C2%25BF%25B9%25C1%25A4%25C0%25CC%25B9%25CC%25C1%25F61.jpg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20250418_143%2F1744969215407NVNoC_JPEG%2F1.jpg"
  ]},
  {"name":"889와규 숭실대점","images":[
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20250402_167%2F1743590969016V1oCg_JPEG%2F1000045071.jpg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20250402_260%2F17435909690134kT9l_JPEG%2F1000045069.jpg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240927_89%2F1727368381741BEeiS_JPEG%2FIMG_1165.jpeg"
  ]},
  {"name":"긴자료코 숭실대입구역점","images":[
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240530_35%2F1717048364228kA0qi_JPEG%2F53D7A0F7-CF8B-4377-9D03-C6BD7F5E5A19.jpeg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240530_156%2F1717048366301sC4rW_JPEG%2F0EC06FF4-C14C-4182-91D3-12CC68DED660.jpeg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240530_193%2F17170483676304iXN2_JPEG%2F088BF800-D039-4F59-9D52-74D59ACE94AF.jpeg"
  ]},
  {"name":"파동추야","images":[
    "https://search.pstatic.net/common/?src=http%3A%2F%2Fimage.nmv.naver.net%2Fblog_2025_04_04_1728%2FLNSDNMyv22_01.jpg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20200510_197%2F1589080554005NODnt_JPEG%2FqYVh5JfuRWwcDnKnsdGBQQ60.jpg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fblogfiles.pstatic.net%2FMjAyNTAzMTJfMTEw%2FMDAxNzQxNzY3NDA1MzMx.d-njBj80arPWXyyfjRe7hYmw_B_-7z5soX7liw1iUfog.SLw_pYZqRcs-rgqB1EUozR58LzffpuSZw8vWFHSGylMg.JPEG%2FIMG_0910.JPG%2F900x913"
  ]},
  {"name":"불타는 소금구이 상도","images":[
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20210607_131%2F1623051762194lih7g_PNG%2F0yZJKjZldiuuMxNCfwxi08Ic.png",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240928_132%2F1727482368645FfOnT_JPEG%2FFullSizeRender_VSCO.jpeg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240928_181%2F17274823686205cDfp_JPEG%2FFullSizeRender_VSCO.jpeg"
  ]},
  {"name":"마루스시","images":[
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20191104_231%2F1572836601955eKMDp_JPEG%2F3YI0EmdX_2SJkgTFFkWD70x5.jpeg.jpg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fpup-review-phinf.pstatic.net%2FMjAyNTA2MTlfMTk0%2FMDAxNzUwMzMwODY5MzM2.-B5u49CpKQ0KBopOjfZ0hb2ezM89uKwHzOBig9s8r5Yg.pgkk1Ojo1mNcqnYOO0PEetTP71vS8DuJ6VbY6D9JMEAg.JPEG%2F1000019627.jpg.jpg%3Ftype%3Dw1500_60_sharpen",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fpup-review-phinf.pstatic.net%2FMjAyNTA3MDZfMjA0%2FMDAxNzUxNzc5ODA2Nzc2.hT4r9Z7uReUdN1omEKOqNEO-8NnmYUoqtCP0ZCuTvIYg.ugj1s1CyCJJGv904Lew69aVro7bL-DcOJmpE_B23oYIg.JPEG%2F20250705_133639%25280%2529.jpg.jpg%3Ftype%3Dw1500_60_sharpen"
  ]},
  {"name":"황새골손칼국수","images":[
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20230925_181%2F1695611357956XhV06_JPEG%2FIMG_1786.jpeg",
    "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyNTA3MjFfODgg%2FMDAxNzUzMTAwNTMzMzI2.2PQa66Nh1bw1-JX8FbP0lkUk0JiPoDEFZu5L5GRLSH8g.C1AawIvE71CCN2pMyynorDQu4-oQxoBG3L9R-ldjeH8g.JPEG%2FKakaoTalk_20250720_154414432_06.jpg",
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fpup-review-phinf.pstatic.net%2FMjAyNTA1MTdfMjUg%2FMDAxNzQ3NDc2ODUyODU3.kusWUMimGmG9XCPzHo47f8gVelHQnwZ2mMlil0uSJRUg.ZCs4Fh5taQ7xCPscr0r1wX9zdhNX0bEJZh3PMQZi_Ucg.JPEG%2F20250417_115115.jpg.jpg%3Ftype%3Dw1500_60_sharpen"
  ]},
  {"name":"나비루","images":[
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20200724_263%2F1595580376523BK6J6_JPEG%2FlXm1aLAhIz2tBknbk50JbxdC.jpg",
    "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyMjExMDlfMjEy%2FMDAxNjY3OTY2OTk5MzEz.brkGhTNl8n0r8w9Tw9l-1ZmTgp1aTribvUquZtkFlOsg.20SYwKyTYKnKefprK3PGUjI0B310S_It9DGw2u-2o04g.JPEG.woney3984%2Foutput_4018480737.jpg%23900x877",
    "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyMDExMjFfMjA5%2FMDAxNjA1ODkzODI2OTk3.wOBtPkAyv_VoKZbndPKGnEzHQ7ZGG-Eqn3tdk0USdAcg.vI6-vMDiyz9IcvKnPSyFqgwO0DjJWLGQLMs4kv4zZIkg.JPEG.stella_eight%2FIMG_4879.JPG%234032x3024"
  ]},
  {"name":"BURRITOZIP","images":[
    "https://d12zq4w4guyljn.cloudfront.net/20250607104425_photo1_51b32e307cad.webp"
  ]},
  {"name":"고추동","images":[
    "https://tse2.mm.bing.net/th/id/OIP.hL2d8Yd9o3ZaVuzXjj9mGgHaFj?r=0&rs=1&pid=ImgDetMain&o=7&rm=3"
  ]},
  {"name":"스팅","images":[
    "https://d12zq4w4guyljn.cloudfront.net/750_750_20250407054149_photo2_5b1840570f99.webp"
  ]},
  {"name":"리얼 후라이","images":[
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fpup-review-phinf.pstatic.net%2FMjAyNDEwMThfMTky%2FMDAxNzI5MjAzNDczMDI2.Zch1LcXS2-z5LJW53IMChoVtRdc2ljFLGJt4TvwqjlMg.RrQvYLOyiFKfdGLHVADgq2iwzRtvblvbEwhPf33Hj9gg.JPEG%2F1000050540.jpg.jpg%3Ftype%3Dw1500_60_sharpen"
  ]},
  {"name":"깍뚝집","images":[
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240801_155%2F17224459079898A3Ca_JPEG%2F1722445660639.jpg"
  ]},
  {"name":"eea","images":[
    "https://search.pstatic.net/common/?src=https%3A%2F%2Fpup-review-phinf.pstatic.net%2FMjAyNDA1MDZfMTk3%2FMDAxNzE0OTc5NzgwMTYx.e6hSHD7PBV1LF-XsOtoQLfPNfD3_GDd-ic0-twv_x6Eg.Vsr131WirPOC8NLMrLAqL2-AjyK5g_c551AuRf6WDwMg.JPEG%2FIMG_5038.jpeg%3Ftype%3Dw1500_60_sharpen"
  ]}
]
JSON

# ===== 준비 =====
mkdir -p "$(dirname "$OUT")"
echo "BEGIN;" > "$OUT"

sql_escape () { sed "s/'/''/g"; }

fetch_first_from_backend () {
  local name="$1"
  curl -sS -G "$BASE/api/places/search" \
    --data-urlencode "station=$STATION" \
    --data-urlencode "q=$name" \
    --data-urlencode "limit=3" \
  | jq -c '.data.items[0] // null'
}

echo "$DATA" | jq -c '.[]' | while read -r row; do
  name=$(jq -r '.name' <<<"$row")
  imgs=$(jq -c '.images' <<<"$row")

  item=$(fetch_first_from_backend "$name")
  if [[ "$item" == "null" || -z "$item" ]]; then
    echo "-- not found: $name" >> "$OUT"
    continue
  fi

  external_id=$(jq -r '.external_id' <<<"$item")
  place_name=$(jq -r '.place_name'  <<<"$item" | sql_escape)
  category_name=$(jq -r '.category_name // ""' <<<"$item" | sql_escape)
  road_address_name=$(jq -r '.road_address_name // ""' <<<"$item" | sql_escape)
  x=$(jq -r '.x' <<<"$item")
  y=$(jq -r '.y' <<<"$item")
  place_url=$(jq -r '.place_url' <<<"$item")

  cat >> "$OUT" <<SQL
-- $name -> $external_id
INSERT INTO places (external_id, place_name, category_name, road_address_name, x, y, place_url, phone, created_at, updated_at)
VALUES ('$external_id', '$place_name', '$category_name', '$road_address_name', '$x', '$y', '$place_url', NULL, now(), now())
ON CONFLICT (external_id) DO UPDATE
SET place_name=EXCLUDED.place_name,
    category_name=EXCLUDED.category_name,
    road_address_name=EXCLUDED.road_address_name,
    x=EXCLUDED.x, y=EXCLUDED.y,
    place_url=EXCLUDED.place_url,
    updated_at=now();

INSERT INTO place_mock (external_id, rating, rating_count, review_snippets, image_urls, opening_hours, updated_at)
VALUES ('$external_id', NULL, NULL, '[]'::jsonb, '$(jq -c <<<"$imgs")'::jsonb, '[]'::jsonb, now())
ON CONFLICT (external_id) DO UPDATE
SET image_urls=EXCLUDED.image_urls,
    updated_at=now();

SQL
done

echo "COMMIT;" >> "$OUT"
echo "Wrote $OUT"
