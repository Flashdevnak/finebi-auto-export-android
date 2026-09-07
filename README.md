# FineBI Auto Export Android

Android app สำหรับเฝ้า `th_update_time` และ Export รายงาน `HUB发车监控 การตรวจสอบการปล่อยรถของHUB` อัตโนมัติผ่าน network route ที่ได้รับอนุญาตของ Flashlink

## First run

1. เชื่อม Flashlink
2. เปิดแอป → แท็บ **FineBI** → Login ตามปกติ
3. กด **Export Excel** ของ FineBI ตามปกติ 1 ครั้ง เพื่อให้แอปเรียนรู้ export request
4. แอปจะลบ `sessionId` ก่อนบันทึก template และจะไม่บันทึก Authorization/Cookie ลงไฟล์
5. หลังจากนั้น Auto Export จะเฝ้า `th_update_time`, FORCE HUB = **Select All**, Export และตรวจ XLSX ให้อัตโนมัติ

ไฟล์จะอยู่ที่ `Downloads/FineBI_Auto_Export/YYYY-MM-DD/`

## Build

GitHub Actions workflow จะ build debug APK อัตโนมัติจาก `main` และอัปโหลด artifact ชื่อ `FineBI-Auto-Export-Android-debug`.
