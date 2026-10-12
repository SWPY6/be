-- 종목↔산업 매핑. db/generate-industry-seed.sh 가 만든다 — 손으로 고치지 않는다.
-- 명세: docs/SPEC-industry.md 「종목 매핑」
--
-- 선행: db/seed-industries.sql · db/seed-markets.sql · db/seed-stocks-*.sql
-- 여러 번 실행해도 결과가 같다 (not exists 로 막는다).
--
-- stock_id 도 market_id 도 박지 않는다 — 로컬과 서버의 auto_increment 가 다르다.
-- 세션 변수도 쓰지 않는다. 문장을 따로 실행하면 변수가 NULL 이 되고, 그러면 아무 행도
-- 맞지 않는데 오류도 나지 않는다(조용한 0건). 각 문장이 스스로 시장을 찾는다.
-- 종목코드와 시장으로 찾아 넣으므로 어느 환경에서든 같게 동작한다.
set names utf8mb4;


insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'AUTOMOBILE')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSDAQ' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '005710', '011320', '012340', '012860', '013310', '013720', '015750', '019540', '024120', '024740',
    '024830', '024910', '031510', '038110', '040610', '043370', '053060', '053270', '053700', '054180',
    '064820', '065500', '066590', '067170', '067570', '071850', '072470', '078590', '080470', '085910',
    '088290', '088390', '090150', '097780', '104040', '105330', '108380', '113810', '118990', '122350',
    '122690', '123040', '123410', '123750', '125490', '126640', '128540', '130740', '151860', '170030',
    '212560', '215360', '234100', '241690', '265560', '290120', '310870', '416180', '419050', '437730',
    '448900', '452400'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'AUTOMOBILE'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'CHEMICAL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSDAQ' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '0010F0', '0015N0', '005290', '007770', '008370', '017890', '018290', '021650', '022220', '027050',
    '027580', '030530', '033050', '033500', '035200', '036640', '036670', '037370', '041930', '045060',
    '048410', '048830', '049550', '050760', '052260', '052420', '052900', '056700', '060230', '060260',
    '065420', '065690', '079000', '080530', '083470', '083660', '086710', '089010', '089980', '091970',
    '092070', '092730', '095500', '097870', '101240', '102710', '104480', '104830', '107600', '110020',
    '112290', '114630', '114840', '115570', '120240', '121600', '123330', '126600', '136410', '137950',
    '159910', '170920', '171010', '171120', '175250', '177350', '187790', '196700', '214260', '217480',
    '220260', '221980', '225430', '226340', '227610', '237880', '238090', '239890', '241710', '250930',
    '251370', '251970', '252500', '260930', '263020', '265740', '278280', '281740', '285800', '286750',
    '294140', '317240', '317330', '318000', '318060', '318410', '332290', '340440', '344860', '348370',
    '352090', '352480', '352700', '352940', '357550', '357780', '365340', '383310', '388610', '393970',
    '406820', '413630', '417500', '420570', '425040', '439090', '445180', '451250', '453860', '457370',
    '475660', '482630', '489460', '489500'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'CHEMICAL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'CONSTRUCTION')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSDAQ' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '001840', '002290', '007680', '011370', '011560', '013120', '016250', '017000', '021320', '025950',
    '026150', '035890', '036190', '036630', '041590', '042940', '044180', '046940', '054940', '056730',
    '060370', '066620', '076080', '091590', '094820', '206400', '282720', '389260'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'CONSTRUCTION'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'FOOD_BEVERAGE')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSDAQ' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '0013V0', '002680', '003310', '004650', '005670', '005860', '005990', '009780', '016790', '018120',
    '023900', '025880', '027710', '035810', '043650', '066360', '073560', '082850', '088910', '092040',
    '103840', '123840', '136480', '188040', '189980', '194700', '195500', '208140', '218150', '222040',
    '222980', '238200', '239610', '260970', '267980', '270870', '276730', '277410', '311390', '318010',
    '353190', '353810', '357230', '368970', '377220', '403490', '435570', '471820'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'FOOD_BEVERAGE'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'RETAIL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSDAQ' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '000440', '0010S0', '004590', '007720', '0117P0', '012700', '013990', '014100', '014190', '016670',
    '016920', '018700', '019010', '019660', '024060', '024940', '024950', '025870', '031330', '031860',
    '032190', '032790', '032940', '033290', '033320', '035080', '036030', '036620', '037400', '038070',
    '038530', '038620', '039740', '042040', '043710', '044960', '045510', '046970', '048470', '048910',
    '050120', '051380', '051390', '051500', '051780', '052020', '052300', '053280', '054410', '060280',
    '060560', '063170', '064090', '064240', '064800', '065150', '065170', '065650', '065660', '066430',
    '066910', '067990', '069920', '080010', '080420', '083640', '086060', '093520', '095340', '099220',
    '102370', '109960', '115160', '115450', '121440', '131220', '140070', '142210', '159580', '178780',
    '204840', '215480', '219550', '223310', '250000', '254490', '257720', '263800', '290720', '299170',
    '308100', '309930', '335870', '342870', '353590', '362320', '405920', '415380', '439580', '472850',
    '481070', '483350'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'RETAIL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'STEEL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSDAQ' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '0004V0', '004780', '005160', '006910', '007530', '008470', '009520', '009620', '009730', '012210',
    '012620', '013030', '014620', '017480', '017510', '018310', '019210', '020400', '021040', '021045',
    '023160', '023440', '023790', '024840', '024880', '025550', '026910', '037760', '038010', '039240',
    '044490', '049830', '053260', '053620', '054540', '059270', '060380', '060480', '069140', '073640',
    '079170', '081150', '090410', '093380', '095190', '096350', '098070', '100130', '101670', '101970',
    '109610', '109860', '119500', '128660', '138070', '140520', '146060', '147830', '148930', '162300',
    '186230', '192390', '198940', '240600', '256630', '258610', '263770', '295310', '312610', '354320',
    '365330', '396300', '452280', '474610', '475230', '484590'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'STEEL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'TRANSPORT')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSDAQ' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '003100', '024800', '032280', '124560'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'TRANSPORT'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'AUTOMOBILE')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSPI' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '000040', '000270', '000300', '000430', '001380', '001420', '001620', '002880', '002920', '003570',
    '003620', '004100', '004105', '005030', '005380', '005385', '005387', '005389', '005850', '006660',
    '009320', '009680', '009900', '010100', '010580', '010690', '010770', '011210', '011330', '012280',
    '012330', '013520', '013870', '015230', '016740', '018500', '021820', '023000', '023800', '023810',
    '024900', '033250', '033530', '041650', '064960', '075180', '090080', '092200', '092780', '123700',
    '143210', '200880', '204320', '214330', '271940', '308170', '378850'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'AUTOMOBILE'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'CHEMICAL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSPI' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '000210', '000215', '000390', '000860', '000880', '00088K', '001340', '001390', '001550', '001570',
    '002100', '002350', '002355', '002360', '002380', '002760', '002790', '002795', '00279K', '002820',
    '002840', '002960', '003080', '003240', '003350', '003650', '003720', '003780', '003830', '004000',
    '004250', '004255', '004430', '004830', '004835', '004840', '004910', '005070', '005420', '005720',
    '005725', '005950', '006650', '006890', '007590', '007690', '008490', '008730', '009830', '009835',
    '010640', '010950', '010955', '011170', '011500', '011780', '011785', '011790', '012610', '014440',
    '014530', '014680', '014820', '014825', '014830', '015890', '017860', '018250', '023450', '024070',
    '024890', '025000', '025620', '025860', '035150', '051630', '051900', '051905', '051910', '051915',
    '055490', '069260', '073240', '078520', '079980', '081000', '083420', '089470', '090350', '090355',
    '090430', '090435', '092230', '093370', '096770', '096775', '100250', '102260', '108670', '108675',
    '120110', '120115', '134380', '161000', '161390', '161890', '163560', '178920', '192820', '214420',
    '226320', '268280', '272550', '278470', '285130', '28513K', '298000', '298020', '298050', '456040',
    '457190', '483650'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'CHEMICAL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'CONSTRUCTION')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSPI' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '000720', '000725', '001260', '001470', '002410', '002460', '002780', '002785', '002787', '002990',
    '002995', '003070', '003075', '004960', '005960', '005965', '006360', '009410', '009415', '010400',
    '010960', '013360', '013580', '013700', '014790', '023960', '028100', '039570', '047040', '051600',
    '097230', '126720', '294870', '375500', '37550K', '37550L', '475150'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'CONSTRUCTION'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'ENERGY')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSPI' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '003480', '004690', '005090', '015760', '017390', '034590', '036460', '071320', '117580', '267290'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'ENERGY'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'FOOD_BEVERAGE')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSPI' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '000080', '000087', '000890', '001130', '001680', '001685', '001790', '001795', '002140', '002600',
    '003230', '003680', '003920', '003925', '003960', '004370', '004410', '004415', '005180', '005300',
    '005305', '005610', '006090', '006980', '007160', '007310', '008040', '011150', '011155', '014710',
    '017810', '023150', '027740', '033780', '033920', '036580', '097950', '097955', '101530', '136490',
    '145990', '145995', '248170', '264900', '26490K', '271560', '280360'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'FOOD_BEVERAGE'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'RETAIL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSPI' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '000050', '000680', '000760', '001120', '001250', '001530', '001740', '002420', '002700', '002810',
    '002870', '003010', '003580', '004060', '004080', '004170', '004270', '005110', '005320', '005360',
    '006060', '006370', '006880', '007070', '007110', '008600', '008770', '008775', '009240', '009810',
    '011760', '011810', '013000', '017940', '018670', '023530', '025530', '026960', '028260', '02826K',
    '031430', '037710', '047050', '067830', '069640', '069960', '071840', '077500', '081660', '088790',
    '101140', '111770', '118000', '122900', '123690', '128820', '139480', '227840', '282330', '339770',
    '381970', '452260', '45226K', '453340', '475560'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'RETAIL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'STEEL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSPI' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '000670', '000970', '001080', '001430', '001770', '001780', '001940', '002220', '002240', '002690',
    '002710', '004020', '004450', '004560', '004890', '005010', '005490', '006110', '006570', '007280',
    '008260', '008350', '008355', '008420', '008970', '009160', '009190', '010130', '010820', '012160',
    '012800', '014280', '014285', '016380', '017960', '018470', '021050', '024090', '025820', '026940',
    '032560', '058430', '058650', '058730', '069460', '069730', '071090', '079550', '084010', '085310',
    '092790', '100090', '103140', '104700', '112610', '120030', '133820', '139990', '155660', '306200',
    '460850', '460860'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'STEEL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'TELECOM')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSPI' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '006490', '017670', '030200', '032640', '126560'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'TELECOM'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'TRANSPORT')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'KOSPI' and country = 'KR' order by market_id limit 1)
  and s.ticker in (
    '000120', '000650', '002320', '003280', '003490', '003495', '004140', '004360', '004365', '005430',
    '005880', '009070', '009180', '011200', '014130', '020560', '028670', '044450', '084670', '086280',
    '089590', '091810', '129260', '272450', '298690', '465770'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'TRANSPORT'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'AUTOMOBILE')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NASDAQ' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'CAAS', 'DORM', 'FOXF', 'FRSX', 'GNTX', 'GT', 'KNDI', 'MPAA', 'NIU', 'STRT',
    'SYPR', 'THRM', 'TSLA', 'VC', 'WKHS', 'XPEL'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'AUTOMOBILE'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'CHEMICAL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NASDAQ' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'BCPC', 'HWKN', 'IOSP', 'LOOP', 'MEOH', 'NTIC', 'TANH'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'CHEMICAL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'CONSTRUCTION')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NASDAQ' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'AAON', 'APOG', 'BBCP', 'CSTE', 'IESC', 'LMB', 'MYRG', 'NWPX', 'PATK', 'RETO',
    'ROAD', 'ROCK', 'STRL', 'UFPI', 'USLM', 'WSC'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'CONSTRUCTION'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'ENERGY')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NASDAQ' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'AMTX', 'AREC', 'ARLP', 'ARTNA', 'BOOM', 'CDZI', 'CLMT', 'CLNE', 'CWCO', 'DMLP',
    'DWSN', 'EPSN', 'FANG', 'GASS', 'GEOS', 'GEVO', 'GLNG', 'GPRE', 'GWRS', 'HNRG',
    'KLXE', 'LNT', 'MARPS', 'MGEE', 'MIND', 'MMLP', 'MSEX', 'MTRX', 'NEXT', 'NFE',
    'OTTR', 'PCYO', 'PNRG', 'PTEN', 'RCON', 'RGCO', 'SND', 'TRMD', 'TUSK', 'VNOM',
    'XEL', 'YORW'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'ENERGY'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'FOOD_BEVERAGE')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NASDAQ' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'ALCO', 'BRID', 'BYND', 'CALM', 'CELH', 'CHSCL', 'CHSCM', 'CHSCN', 'CHSCO', 'CHSCP',
    'COKE', 'FAMI', 'FIZZ', 'FRPT', 'FTFT', 'HAIN', 'JBSS', 'JJSF', 'JVA', 'LMNR',
    'LWAY', 'MDLZ', 'MGPI', 'MNST', 'PEP', 'PETZ', 'PPC', 'RKDA', 'RMCF', 'SEED',
    'SENEA', 'SENEB', 'SMPL', 'VFF', 'WVVI', 'WVVIP'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'FOOD_BEVERAGE'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'RETAIL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NASDAQ' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'AMZN', 'ANDE', 'BKNG', 'BLNK', 'BZUN', 'CASY', 'CHEF', 'COST', 'CRMT', 'CTRN',
    'DLTH', 'DLTR', 'EBAY', 'EDUC', 'EXPE', 'EYE', 'FIVE', 'FLWS', 'FNKO', 'GNLN',
    'GO', 'GRPN', 'HFFG', 'IMKTA', 'JD', 'KXIN', 'LE', 'LKQ', 'LQDT', 'MELI',
    'MMYT', 'MNRO', 'OLLI', 'ORLY', 'PDD', 'PETS', 'PLCE', 'POOL', 'PRTS', 'PSMT',
    'REAL', 'ROST', 'SFIX', 'SFM', 'SPWH', 'TSCO', 'ULTA', 'URBN', 'UXIN', 'VLGEA',
    'WEYS', 'WINA', 'YI', 'YJ', 'YTRA', 'ZUMZ'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'RETAIL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'STEEL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NASDAQ' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'CENX', 'CHNR', 'GSM', 'KALU', 'METC', 'RGLD', 'SSRM', 'STLD', 'USAU', 'ZKIN'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'STEEL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'TELECOM')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NASDAQ' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'ATEX', 'ATNI', 'BAND', 'CCOI', 'GOGO', 'IRDM', 'SHEN', 'SIFY', 'SPOK', 'TIGO',
    'TMUS', 'VEON', 'VOD'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'TELECOM'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'TRANSPORT')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NASDAQ' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'AAL', 'AIRT', 'ALGT', 'ARCB', 'CAR', 'CHRW', 'CSX', 'CTRM', 'EDRY', 'ESEA',
    'FWRD', 'GLBS', 'HTLD', 'HUBG', 'JBHT', 'JBLU', 'LSTR', 'LYFT', 'MRTN', 'ODFL',
    'OMAB', 'PANL', 'PXS', 'RYAAY', 'SAIA', 'SBLK', 'SHIP', 'SKYW', 'UAL', 'ULH',
    'WERN'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'TRANSPORT'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'AUTOMOBILE')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NYSE' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'ADNT', 'ALV', 'APTV', 'BWA', 'CPS', 'DAN', 'F', 'GM', 'HMC', 'HOG',
    'LCII', 'LEA', 'MGA', 'MOD', 'NIO', 'RACE', 'SMP', 'SRI', 'THO', 'TM',
    'WGO'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'AUTOMOBILE'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'CHEMICAL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NYSE' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'ALB', 'APD', 'ASH', 'ASIX', 'AVD', 'AXTA', 'CBT', 'CC', 'CE', 'CF',
    'CTVA', 'DD', 'DOW', 'ECL', 'EMN', 'ESI', 'FF', 'FMC', 'FTK', 'FUL',
    'HUN', 'ICL', 'IFF', 'IPI', 'KOP', 'KRO', 'KWR', 'LXU', 'LYB', 'MOS',
    'MTX', 'NEU', 'NGVT', 'NTR', 'OEC', 'OLN', 'PPG', 'RPM', 'RYAM', 'SCL',
    'SHW', 'SMG', 'SQM', 'SSL', 'SXT', 'TG', 'TROX', 'UAN', 'VHI', 'VVV',
    'WLK', 'WLKP'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'CHEMICAL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'CONSTRUCTION')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NYSE' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'ACA', 'ACM', 'AGX', 'ALLE', 'AMRC', 'AOS', 'AWI', 'CPAC', 'CRH', 'CX',
    'DY', 'EME', 'EXP', 'FIX', 'FLR', 'GFF', 'GVA', 'JCI', 'JELD', 'JHX',
    'LII', 'LOMA', 'MAS', 'MLM', 'MTZ', 'NX', 'OC', 'ORN', 'PWR', 'REZI',
    'SSD', 'TPC', 'TREX', 'VMC', 'VMI', 'WMS'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'CONSTRUCTION'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'ENERGY')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NYSE' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'AEE', 'AES', 'AM', 'AQN', 'AR', 'AROC', 'ASC', 'ASPN', 'ATO', 'AVA',
    'AWK', 'AWR', 'BEP', 'BIP', 'BKH', 'BP', 'BSM', 'BTU', 'CAPL', 'CCJ',
    'CEPU', 'CIG', 'CIG/C', 'CLB', 'CMS', 'CMS/B', 'CNP', 'CNQ', 'CNX', 'COP',
    'CPK', 'CRK', 'CRT', 'CVE', 'CVI', 'CVX', 'CWT', 'D', 'DHT', 'DK',
    'DKL', 'DLNG', 'DLNG/A', 'DTE', 'DUK', 'DVN', 'E', 'EC', 'ED', 'EDN',
    'EGY', 'EIX', 'ENB', 'ENIC', 'EOG', 'EPD', 'EQNR', 'EQT', 'ES', 'ETR',
    'FE', 'FET', 'FRO', 'FTI', 'FTS', 'GEL', 'GLP', 'GNE', 'GPRK', 'HAL',
    'HE', 'HESM', 'HP', 'IDA', 'INSW', 'KEN', 'KEP', 'KMI', 'KNOP', 'KOS',
    'KRP', 'LBRT', 'LPG', 'MDU', 'MGY', 'MPC', 'MPLX', 'MTDR', 'MTR', 'MUR',
    'NAT', 'NBR', 'NC', 'NEE', 'NFG', 'NGG', 'NGL', 'NGS', 'NI', 'NJR',
    'NOA', 'NOV', 'NRG', 'NRP', 'NRT', 'NVGS', 'NWN', 'OGE', 'OGS', 'OII',
    'OIS', 'OKE', 'ORA', 'OXY', 'PAM', 'PARR', 'PBA', 'PBF', 'PBR', 'PBR/A',
    'PBT', 'PCG', 'PDS', 'PEG', 'PNW', 'POR', 'PPL', 'PRT', 'PSX', 'PUMP',
    'PVL', 'RES', 'REX', 'RIG', 'RNGR', 'RRC', 'SBR', 'SBS', 'SD', 'SFL',
    'SGU', 'SJT', 'SLB', 'SM', 'SMHI', 'SO', 'SPH', 'SR', 'SRE', 'STNG',
    'SU', 'SWX', 'TAC', 'TALO', 'TDW', 'TGS', 'TK', 'TNK', 'TPL', 'TRGP',
    'TRP', 'TS', 'TTI', 'TVC', 'TVE', 'UGI', 'UGP', 'UTL', 'VET', 'VIST',
    'VLO', 'VOC', 'VST', 'WEC', 'WES', 'WHD', 'WMB', 'WTI', 'WTTR', 'XOM',
    'YPF'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'ENERGY'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'FOOD_BEVERAGE')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NYSE' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'ABEV', 'ADM', 'AGRO', 'AKO/A', 'AKO/B', 'BF/A', 'BF/B', 'BG', 'BGS', 'BTI',
    'BUD', 'CAG', 'CCU', 'DAR', 'DEO', 'FLO', 'FMX', 'GIS', 'HRL', 'HSY',
    'INGR', 'KO', 'KOF', 'LND', 'LW', 'MKC', 'MKC/V', 'MO', 'NOMD', 'PM',
    'POST', 'SAM', 'SJM', 'STZ', 'TAP', 'TAP/A', 'TPB', 'TR', 'TSN', 'UVV'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'FOOD_BEVERAGE'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'RETAIL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NYSE' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'AAP', 'ABG', 'AEO', 'AN', 'ANF', 'AZO', 'BABA', 'BBW', 'BBY', 'BJ',
    'BKE', 'BNED', 'BOOT', 'BURL', 'CAL', 'CANG', 'CATO', 'CHWY', 'CVNA', 'CWH',
    'DBI', 'DG', 'DKS', 'FND', 'FVRR', 'GCO', 'GME', 'GPC', 'GPI', 'HD',
    'HVT', 'HVT/A', 'HZO', 'JILL', 'JMIA', 'KMX', 'KR', 'KSS', 'LAD', 'LITB',
    'LOW', 'M', 'MOGU', 'MUSA', 'NGVC', 'PAG', 'PFGC', 'RH', 'RVLV', 'SAH',
    'SBH', 'SIG', 'SSTK', 'SYY', 'TGT', 'TJX', 'TLYS', 'UNFI', 'USFD', 'VIPS',
    'W', 'WMK', 'WSM'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'RETAIL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'STEEL')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NYSE' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'AA', 'AEM', 'AG', 'AGI', 'AP', 'ATI', 'AU', 'BHP', 'BVN', 'CDE',
    'CLF', 'CMC', 'CMP', 'CRS', 'CSTM', 'DRD', 'EGO', 'EXK', 'FCX', 'FNV',
    'FSM', 'GFI', 'GGB', 'HBM', 'HCC', 'HL', 'HL/B', 'HMY', 'IAG', 'KGC',
    'LAC', 'MEC', 'MSB', 'MT', 'MTRN', 'MUX', 'NEM', 'NEXA', 'NUE', 'OR',
    'PKX', 'RIO', 'RS', 'SA', 'SCCO', 'SID', 'SXC', 'TECK', 'TX', 'VALE',
    'WOR', 'WPM'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'STEEL'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'TELECOM')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NYSE' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'AMX', 'BCE', 'CHT', 'IDT', 'KT', 'OOMA', 'PHI', 'RCI', 'SKM', 'T',
    'TDS', 'TEO', 'TKC', 'TLK', 'TU', 'VZ'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'TELECOM'));

insert into stock_industries (stock_id, industry_id)
select s.stock_id, (select industry_id from industries where name = 'TRANSPORT')
from stocks s
where s.market_id = (select market_id from markets
                        where code = 'NYSE' and country = 'US' order by market_id limit 1)
  and s.ticker in (
    'ALK', 'ASR', 'CAAP', 'CMRE', 'CMRE/B', 'CMRE/C', 'CMRE/D', 'CNI', 'CP', 'CPA',
    'DAC', 'DAL', 'DSX', 'DSX/B', 'FDX', 'GNK', 'GSL', 'GSL/B', 'KEX', 'KNX',
    'LUV', 'MATX', 'NMM', 'NSC', 'PAC', 'R', 'SB', 'SB/C', 'SB/D', 'SNDR',
    'UBER', 'UNP', 'UPS', 'VLRS', 'XPO', 'ZTO'
  )
  and not exists (select 1 from stock_industries si
                  where si.stock_id = s.stock_id
                    and si.industry_id = (select industry_id from industries where name = 'TRANSPORT'));

-- 매핑 1599건
