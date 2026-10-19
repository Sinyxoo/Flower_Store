/* ЗАГЛУШКА: демо-данные. Когда заработает бэкенд — удалите этот файл
   и строки <script src="...mock-data.js"> из HTML. api.js сам перейдёт на /api/... */
window.MOCK = {
  categories: [{id:1,name:'Розы'},{id:2,name:'Тюльпаны'},{id:3,name:'Букеты'},{id:4,name:'Горшечные'}],
  products: [
    {id:1,name:'Роза красная, 60 см',categoryId:1,price:4.5,stock:100,description:'Классическая красная роза с длинным стеблем.',imageUrl:'',visible:true},
    {id:2,name:'Роза кремовая, 50 см',categoryId:1,price:3.8,stock:40,description:'Нежный кремовый оттенок.',imageUrl:'',visible:true},
    {id:3,name:'Тюльпаны микс, 15 шт.',categoryId:2,price:36,stock:12,description:'Весенний букет из разноцветных тюльпанов.',imageUrl:'',visible:true},
    {id:4,name:'Букет «Нежность»',categoryId:3,price:95,stock:5,description:'Пионовидные розы, эустома, эвкалипт.',imageUrl:'',visible:true},
    {id:5,name:'Букет «Летний луг»',categoryId:3,price:70,stock:0,description:'Полевые цветы в крафтовой упаковке.',imageUrl:'',visible:true},
    {id:6,name:'Орхидея фаленопсис',categoryId:4,price:85,stock:8,description:'Орхидея в керамическом кашпо.',imageUrl:'',visible:true},
    {id:7,name:'Подсолнухи, 5 шт.',categoryId:3,price:45,stock:15,description:'Яркий солнечный букет.',imageUrl:'',visible:true},
    {id:8,name:'Гортензия белая',categoryId:4,price:60,stock:0,description:'Скрыта с сайта.',imageUrl:'',visible:false}
  ],
  orders: [
    {id:101,customerName:'Анна Иванова',email:'anna@example.com',phone:'+375 29 111-22-33',address:'ул. Притыцкого, 29',createdAt:'2026-10-05',status:'new',items:[{name:'Роза красная, 60 см',qty:11,price:4.5}],total:49.5},
    {id:100,customerName:'Пётр Сидоров',email:'petr@example.com',phone:'+375 44 444-55-66',address:'пр. Победителей, 12',createdAt:'2026-10-03',status:'delivered',items:[{name:'Букет «Нежность»',qty:1,price:95}],total:95},
    {id:99,customerName:'Анна Иванова',email:'anna@example.com',phone:'+375 29 111-22-33',address:'ул. Притыцкого, 29',createdAt:'2026-09-28',status:'cancelled',items:[{name:'Орхидея фаленопсис',qty:1,price:85}],total:85}
  ],
  users: [
    {id:1,name:'Администратор',email:'admin@flower.local',phone:'',address:'',role:'ADMIN'},
    {id:2,name:'Анна Иванова',email:'anna@example.com',phone:'+375 29 111-22-33',address:'ул. Притыцкого, 29',role:'USER'},
    {id:3,name:'Пётр Сидоров',email:'petr@example.com',phone:'+375 44 444-55-66',address:'пр. Победителей, 12',role:'USER'}
  ]
};
