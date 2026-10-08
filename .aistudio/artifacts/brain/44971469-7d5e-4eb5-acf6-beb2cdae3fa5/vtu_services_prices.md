# Daniel VTU Services & Prices Reference

## 1. Gsubz Supabase Edge Function URL

All VTU plan lookups and purchase transactions in the app are routed strictly through your Supabase Edge Function URL:

- **Gsubz Supabase Edge Function URL:**
  `https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Gsubz-VTU-Services`
- **Supabase Project Base URL:**
  `https://yjymxdzdhvbdjramlipg.supabase.co`
- **Supabase Prices Table Endpoint:**
  `https://yjymxdzdhvbdjramlipg.supabase.co/rest/v1/vtu_prices`

---

## 2. Mobile Data Bundle Prices (`service_type = "data"`)

### 2.1 MTN Data Plans (`mtn_sme` & `mtn_gifting`)

| Plan ID | Service ID (`serviceID`) | Plan Code (`plan`) | Category | Data Volume | Validity | Price (₦) | Original Price (₦) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `mtn_sme_500mb` | `mtn_sme` | `179` | SME | 500 MB | 7 Days | **₦299.00** | ₦350.00 |
| `mtn_sme_1gb` | `mtn_sme` | `166` | SME | 1.0 GB | 30 Days | **₦399.00** | ₦450.00 |
| `mtn_sme_2gb` | `mtn_sme` | `167` | SME | 2.0 GB | 30 Days | **₦899.00** | ₦1,000.00 |
| `mtn_sme_3gb` | `mtn_sme` | `168` | SME | 3.0 GB | 30 Days | **₦1,348.00** | ₦1,500.00 |
| `mtn_sme_5gb` | `mtn_sme` | `357` | SME | 5.0 GB | 30 Days | **₦1,799.00** | ₦2,000.00 |
| `mtn_cg_500mb` | `mtn_sme` | `179` | CORPORATE | 500 MB | 7 Days | **₦299.00** | ₦350.00 |
| `mtn_cg_1gb` | `mtn_gifting` | `166` | CORPORATE | 1.0 GB | 30 Days | **₦399.00** | ₦450.00 |
| `mtn_cg_2gb` | `mtn_gifting` | `366` | CORPORATE | 2.0 GB | 30 Days | **₦899.00** | ₦1,000.00 |
| `mtn_cg_3gb` | `mtn_gifting` | `168` | CORPORATE | 3.0 GB | 30 Days | **₦1,348.00** | ₦1,500.00 |
| `mtn_cg_5gb` | `mtn_gifting` | `357` | CORPORATE | 5.0 GB | 30 Days | **₦1,799.00** | ₦2,000.00 |
| `mtn_daily_750mb` | `mtn_gifting` | `373` | DAILY_WEEKLY | 750 MB + 1Hr YT/IG/TT | 3 Days | **₦436.00** | ₦500.00 |
| `mtn_social_1_2gb` | `mtn_gifting` | `374` | GIFTING | 1.2 GB (All Socials) | 30 Days | **₦436.00** | ₦500.00 |
| `mtn_daily_1_5gb` | `mtn_gifting` | `355` | DAILY_WEEKLY | 1.5 GB | 2 Days | **₦582.00** | ₦650.00 |
| `mtn_weekly_2_5gb` | `mtn_gifting` | `365` | DAILY_WEEKLY | 2.5 GB | 2 Days | **₦873.00** | ₦950.00 |
| `mtn_gifting_2gb` | `mtn_gifting` | `366` | GIFTING | 2.0 GB Gifting | 30 Days | **₦899.00** | ₦1,000.00 |
| `mtn_daily_3_2gb` | `mtn_gifting` | `360` | DAILY_WEEKLY | 3.2 GB | 2 Days | **₦970.00** | ₦1,100.00 |
| `mtn_daily_3_5gb` | `mtn_gifting` | `376` | DAILY_WEEKLY | 3.5 GB | 1 Day | **₦970.00** | ₦1,100.00 |

---

### 2.2 Airtel Data Plans (`airtel_sme` & `airtel_gifting`)

| Plan ID | Service ID (`serviceID`) | Plan Code (`plan`) | Category | Data Volume | Validity | Price (₦) | Original Price (₦) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `airtel_sme_300mb` | `airtel_sme` | `476` | SME | 300 MB | 2 Days | **₦110.00** | ₦150.00 |
| `airtel_sme_600mb` | `airtel_sme` | `478` | SME | 600 MB | 2 Days | **₦210.00** | ₦250.00 |
| `airtel_sme_1gb` | `airtel_sme` | `484` | SME | 1.5 GB | 1 Day | **₦392.00** | ₦450.00 |
| `airtel_sme_2gb` | `airtel_sme` | `485` | SME | 3.0 GB | 2 Days | **₦735.00** | ₦850.00 |
| `airtel_sme_5gb` | `airtel_sme` | `483` | SME | 10.0 GB | 30 Days | **₦3,010.00** | ₦3,500.00 |
| `airtel_cg_300mb` | `airtel_gifting` | `555` | CORPORATE | 300 MB | 2 Days | **₦294.00** | ₦340.00 |
| `airtel_cg_500mb` | `airtel_gifting` | `144` | CORPORATE | 500 MB | 7 Days | **₦490.00** | ₦550.00 |
| `airtel_cg_1gb` | `airtel_gifting` | `324` | CORPORATE | 1.0 GB | 7 Days | **₦780.00** | ₦860.00 |
| `airtel_cg_2gb` | `airtel_gifting` | `427` | CORPORATE | 2.0 GB | 30 Days | **₦1,470.00** | ₦1,600.00 |
| `airtel_cg_5gb` | `airtel_gifting` | `148` | CORPORATE | 4.0 GB | 30 Days | **₦2,462.50** | ₦2,700.00 |
| `airtel_daily_100mb` | `airtel_gifting` | `552` | DAILY_WEEKLY | 100 MB | 1 Day | **₦98.00** | ₦120.00 |
| `airtel_daily_230mb` | `airtel_gifting` | `389` | DAILY_WEEKLY | 230 MB | 2 Days | **₦196.00** | ₦220.00 |
| `airtel_daily_1gb` | `airtel_gifting` | `426` | DAILY_WEEKLY | 1.0 GB (Socials) | 1 Day | **₦294.50** | ₦350.00 |
| `airtel_weekly_500mb` | `airtel_gifting` | `144` | DAILY_WEEKLY | 500 MB | 7 Days | **₦490.00** | ₦550.00 |
| `airtel_daily_2gb` | `airtel_gifting` | `145` | DAILY_WEEKLY | 2.0 GB | 2 Days | **₦586.00** | ₦650.00 |
| `airtel_weekly_1gb` | `airtel_gifting` | `324` | DAILY_WEEKLY | 1.0 GB | 7 Days | **₦780.00** | ₦850.00 |
| `airtel_daily_4gb` | `airtel_gifting` | `146` | DAILY_WEEKLY | 4.0 GB | 2 Days | **₦980.00** | ₦1,100.00 |

---

### 2.3 Glo Data Plans (`glo_data` & `glo_sme`)

| Plan ID | Service ID (`serviceID`) | Plan Code (`plan`) | Category | Data Volume | Validity | Price (₦) | Original Price (₦) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `glo_cg_200mb` | `glo_data` | `200` | CORPORATE | 200 MB | 14 Days | **₦120.00** | ₦150.00 |
| `glo_cg_500mb` | `glo_data` | `500` | CORPORATE | 500 MB | 30 Days | **₦200.00** | ₦250.00 |
| `glo_sme_1gb` | `glo_data` | `1000` | CORPORATE | 1.0 GB | 30 Days | **₦399.00** | ₦450.00 |
| `glo_sme_2gb` | `glo_data` | `2000` | CORPORATE | 2.0 GB | 30 Days | **₦780.00** | ₦850.00 |
| `glo_cg_3gb_3d` | `glo_data` | `3003` | DAILY_WEEKLY | 3.0 GB | 3 Days | **₦1,017.00** | ₦1,150.00 |
| `glo_cg_3gb_7d` | `glo_data` | `3007` | DAILY_WEEKLY | 3.0 GB | 7 Days | **₦1,065.00** | ₦1,200.00 |
| `glo_sme_750mb` | `glo_sme` | `491` | SME | 750 MB | 1 Day | **₦189.00** | ₦220.00 |
| `glo_sme_1_5gb` | `glo_sme` | `492` | SME | 1.5 GB | 1 Day | **₦284.00** | ₦320.00 |
| `glo_sme_2_5gb` | `glo_sme` | `493` | SME | 2.5 GB | 2 Days | **₦473.00** | ₦550.00 |
| `glo_sme_10gb` | `glo_sme` | `494` | SME | 10.0 GB | 7 Days | **₦1,890.00** | ₦2,100.00 |

---

### 2.4 9mobile Data Plans (`etisalat_data`)

| Plan ID | Service ID (`serviceID`) | Plan Code (`plan`) | Category | Data Volume | Validity | Price (₦) | Original Price (₦) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `9mob_sme_500mb` | `etisalat_data` | `182` | SME | 500 MB | 30 Days | **₦250.00** | ₦300.00 |
| `9mob_sme_1gb` | `etisalat_data` | `298` | SME | 1.0 GB | 30 Days | **₦499.00** | ₦550.00 |
| `9mob_sme_1_5gb` | `etisalat_data` | `300` | SME | 1.5 GB | 30 Days | **₦748.00** | ₦850.00 |
| `9mob_sme_2gb` | `etisalat_data` | `299` | SME | 2.0 GB | 30 Days | **₦998.00** | ₦1,100.00 |
| `9mob_sme_3gb` | `etisalat_data` | `303` | CORPORATE | 3.0 GB | 30 Days | **₦1,498.00** | ₦1,650.00 |
| `9mob_sme_4gb` | `etisalat_data` | `347` | CORPORATE | 4.0 GB | 30 Days | **₦1,997.00** | ₦2,200.00 |
| `9mob_sme_4_5gb` | `etisalat_data` | `348` | CORPORATE | 4.5 GB | 30 Days | **₦2,247.00** | ₦2,450.00 |
| `9mob_sme_5gb` | `etisalat_data` | `304` | CORPORATE | 5.0 GB | 30 Days | **₦2,496.00** | ₦2,700.00 |

---

## 3. Cable TV Subscription Prices (`service_type = "cable"`)

### 3.1 DStv Bouquets (`serviceID = "dstv"`)

| Bouquet ID | Service ID (`serviceID`) | Plan Code (`plan`) | Bouquet Name | Channels | Price (₦) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `dstv_padi` | `dstv` | `dstv-padi` | DStv Padi | 45 | **₦4,400.00** |
| `dstv_yanga` | `dstv` | `dstv-yanga` | DStv Yanga | 85 | **₦6,000.00** |
| `dstv_confam` | `dstv` | `dstv-confam` | DStv Confam | 120 | **₦11,000.00** |
| `dstv_compact` | `dstv` | `dstv79` | DStv Compact | 145 | **₦19,000.00** |
| `dstv_compact_plus` | `dstv` | `dstv7` | DStv Compact Plus | 160 | **₦30,000.00** |
| `dstv_premium` | `dstv` | `dstv3` | DStv Premium | 175 | **₦44,500.00** |
| `dstv_premium_asia` | `dstv` | `dstv10` | DStv Premium-Asia | 185 | **₦50,500.00** |
| `dstv_premium_french` | `dstv` | `dstv9` | DStv Premium-French | 195 | **₦69,000.00** |

---

### 3.2 GOtv Bouquets (`serviceID = "gotv"`)

| Bouquet ID | Service ID (`serviceID`) | Plan Code (`plan`) | Bouquet Name | Channels | Price (₦) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `gotv_smallie` | `gotv` | `gotv-smallie` | GOtv Smallie (Monthly) | 35 | **₦1,900.00** |
| `gotv_jinja` | `gotv` | `gotv-jinja` | GOtv Jinja | 47 | **₦3,900.00** |
| `gotv_jolli` | `gotv` | `gotv-jolli` | GOtv Jolli | 68 | **₦5,800.00** |
| `gotv_max` | `gotv` | `gotv-max` | GOtv Max | 75 | **₦8,500.00** |
| `gotv_supa` | `gotv` | `gotv-supa` | GOtv Supa (Monthly) | 80 | **₦11,400.00** |
| `gotv_supa_plus` | `gotv` | `gotv-supa-plus` | GOtv Supa Plus (Monthly) | 85 | **₦16,800.00** |
| `gotv_smallie_quarterly` | `gotv` | `gotv-smallie-3months` | GOtv Smallie (Quarterly) | 35 | **₦5,100.00** |
| `gotv_smallie_yearly` | `gotv` | `gotv-smallie-1year` | GOtv Smallie (Yearly) | 35 | **₦15,000.00** |

---

### 3.3 StarTimes Bouquets (`serviceID = "startimes"`)

| Bouquet ID | Service ID (`serviceID`) | Plan Code (`plan`) | Bouquet Name | Channels | Price (₦) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `star_nova` | `startimes` | `nova` | Nova Monthly | 38 | **₦2,100.00** |
| `star_basic` | `startimes` | `basic` | Basic (Antenna) Monthly | 60 | **₦4,000.00** |
| `star_smart` | `startimes` | `smart` | Basic (Dish) / Smart Monthly | 70 | **₦5,100.00** |
| `star_classic` | `startimes` | `classic` | Classic (Antenna) Monthly | 80 | **₦6,000.00** |
| `star_super` | `startimes` | `super` | Super (Dish) Monthly | 95 | **₦9,800.00** |
| `star_nova_weekly` | `startimes` | `nova-weekly` | Nova Weekly | 38 | **₦700.00** |
| `star_basic_weekly` | `startimes` | `basic-weekly` | Basic Weekly | 60 | **₦1,400.00** |
| `star_smart_weekly` | `startimes` | `smart-weekly` | Smart Weekly | 70 | **₦1,700.00** |

---

### 3.4 Showmax Plans (`serviceID = "showmax"`)

| Bouquet ID | Service ID (`serviceID`) | Plan Code (`plan`) | Bouquet Name | Channels | Price (₦) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `showmax_mobile` | `showmax` | `mobile_only_3` | Showmax Mobile Only | 50 | **₦1,600.00** |
| `showmax_full` | `showmax` | `full_3` | Showmax Full | 80 | **₦3,500.00** |
| `showmax_sports_mobile` | `showmax` | `sports_mobile_only_3` | Showmax Sports Mobile Only | 95 | **₦4,000.00** |
| `showmax_sports_full` | `showmax` | `sports_full_3` | Showmax Sports Full | 120 | **₦6,500.00** |

---

## 4. Education Result Checker & Exam PIN Prices (`service_type = "education"` / `"data"`)

| Exam ID | Institute | Service ID (`serviceID`) | Plan Code (`plan`) | Description | Price (₦) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `waec_pin` | **WAEC** | `waec` | `WAEC` | WAEC Registration & Result Checker PIN | **₦5,100.00** |
| `neco_token` | **NECO** | `neco` | `NECO` | NECO Result Checker PIN | **₦2,100.00** |
| `nabteb_pin` | **NABTEB** | `nabteb` | `NABTEB` | NABTEB Result Checker PIN | **₦900.00** |
| `jamb_utme` | **JAMB** | `jamb` | `JAMB` | JAMB UTME Profile Registration e-PIN | **₦6,200.00** |

---

## 5. Airtime VTU Services (`service_type = "airtime"`)

Airtime is user-entered (any face value amount) with quick-select denominations and dynamic cashback read from your Supabase `vtu_prices` table (`plan = ""`):

| Network | Service ID (`serviceID`) | Plan (`plan`) | Quick-Select Denominations (₦) | Pricing Rule |
| :--- | :--- | :--- | :--- | :--- |
| **MTN** | `mtn` | `""` | ₦100, ₦200, ₦500, ₦1,000, ₦2,000, ₦5,000 | 1:1 Face Value (`cashback_percent` read live from `vtu_prices`) |
| **Airtel** | `airtel` | `""` | ₦100, ₦200, ₦500, ₦1,000, ₦2,000, ₦5,000 | 1:1 Face Value (`cashback_percent` read live from `vtu_prices`) |
| **GLO** | `glo` | `""` | ₦100, ₦200, ₦500, ₦1,000, ₦2,000, ₦5,000 | 1:1 Face Value (`cashback_percent` read live from `vtu_prices`) |
| **9mobile** | `etisalat` / `9mobile` | `""` | ₦100, ₦200, ₦500, ₦1,000, ₦2,000, ₦5,000 | 1:1 Face Value (`cashback_percent` read live from `vtu_prices`) |

---

## 6. Electricity Bill Payment Services (`service_type = "electricity"`)

Electricity tokens/bills are user-entered amounts (`Prepaid` or `Postpaid`) across all 10 Distribution Companies (DisCos):

| DisCo ID | Short Name | Service ID (`serviceID`) | Coverage Area | Meter Types (`plan`) | Quick-Select Amounts (₦) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `ikedc` | **IKEDC** | `ikeja-electric` | Lagos Mainland & Environs | `prepaid`, `postpaid` | ₦1,000, ₦2,000, ₦5,000, ₦10,000, ₦20,000 |
| `ekedc` | **EKEDC** | `eko-electric` | Lagos Island & Lekki/VI | `prepaid`, `postpaid` | ₦1,000, ₦2,000, ₦5,000, ₦10,000, ₦20,000 |
| `aedc` | **AEDC** | `abuja-electric` | FCT Abuja, Nasarawa, Kogi, Niger | `prepaid`, `postpaid` | ₦1,000, ₦2,000, ₦5,000, ₦10,000, ₦20,000 |
| `ibedc` | **IBEDC** | `ibadan-electric` | Oyo, Ogun, Osun, Kwara | `prepaid`, `postpaid` | ₦1,000, ₦2,000, ₦5,000, ₦10,000, ₦20,000 |
| `phed` | **PHED** | `portharcourt-electric` | Rivers, Bayelsa, Cross River, Akwa Ibom | `prepaid`, `postpaid` | ₦1,000, ₦2,000, ₦5,000, ₦10,000, ₦20,000 |
| `eedc` | **EEDC** | `enugu-electric` | Enugu, Abia, Anambra, Imo, Ebonyi | `prepaid`, `postpaid` | ₦1,000, ₦2,000, ₦5,000, ₦10,000, ₦20,000 |
| `kedco` | **KEDCO** | `kano-electric` | Kano, Katsina, Jigawa | `prepaid`, `postpaid` | ₦1,000, ₦2,000, ₦5,000, ₦10,000, ₦20,000 |
| `jed` | **JED** | `jos-electric` | Plateau, Bauchi, Benue, Gombe | `prepaid`, `postpaid` | ₦1,000, ₦2,000, ₦5,000, ₦10,000, ₦20,000 |
| `kaedco` | **KAEDCO** | `kaduna-electric` | Kaduna, Kebbi, Sokoto, Zamfara | `prepaid`, `postpaid` | ₦1,000, ₦2,000, ₦5,000, ₦10,000, ₦20,000 |
| `bedc` | **BEDC** | `benin-electric` | Edo, Delta, Ondo, Ekiti | `prepaid`, `postpaid` | ₦1,000, ₦2,000, ₦5,000, ₦10,000, ₦20,000 |
