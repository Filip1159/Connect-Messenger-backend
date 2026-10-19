insert into users (id, username, name, surname, password) values
(1, 'janek007', 'Jan', 'Kowalski', '{bcrypt}$2y$12$yJKwJH8SWLBGSvPiggXQdelhMuZNhbQI3WIrNltRN1cE9JcNoNRVi'),  -- qwerty
(2, 'ewa-nowak', 'Ewa', 'Nowak', '{bcrypt}$2y$12$s8GQ5Prs915abrGV/WxCquQuas1xvnqaDZ2iQrLzAKp2TthdfEBgK'),  -- abcd
(3, 'mrsmith', 'John', 'Smith', '{bcrypt}$2y$12$CQtho0.NJ6JCbh4PE.eAUOHPfj9OhP3PNA9KxU5jj.Gmdrd.U4wa6'),  -- hahaxd
(4, 'grace01', 'Grace', 'Thomson', '{bcrypt}$2y$12$H0pQUvf9NWQnN.A9p3cdWum0IHLGfKCCkBagoCdISAUT0hjfMq28a');  -- 1234

insert into chats (id) values (1), (2), (3), (4);

insert into status (user_id, chat_id, message_id, time) values
(1, 1, 1, '2021-07-07T22:24:59'), (2, 1, 3, '2021-07-07T22:24:59'), -- janek007 and ewa-nowak in conversation 1
(1, 2, 13, '2021-07-07T22:24:59'), (3, 2, 12, '2021-07-07T22:24:59'), -- janek007 and mrsmith in conversation 2
(4, 3, 18, '2021-07-07T22:24:59'), (3, 3, 19, '2021-07-07T22:24:59'), -- grace01 and mrsmith in conversation 3
(1, 4, 24, '2021-07-07T22:24:59'), (4, 4, 25, '2021-07-07T22:24:59'); -- janek007 and grace01 in conversation 4

insert into messages (id, chat_id, user_id, time, type, content) values
(1, 1, 1, '2021-07-07T22:23:29', 'TEXT', 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Duis et leo eget turpis ornare tempor sed at tortor. Curabitur elit magna, sollicitudin vel condimentum ac, euismod sed mi. Suspendisse potenti.'), -- janek007 to ewa-nowak
(2, 1, 2, '2021-07-07T22:24:49', 'TEXT', 'Integer vestibulum lacus sit amet magna feugiat ultricies. Aenean ullamcorper lorem quis arcu pharetra'),
(3, 1, 2, '2021-07-07T22:25:10', 'TEXT', 'Sed nisi massa, ullamcorper at venenatis accumsan, fermentum sit amet neque.'),

(4, 2, 3, '2021-07-08T17:57:14', 'TEXT', 'Nunc vehicula viverra dolor, sed sagittis libero lacinia ac.'),  -- mrsmith to janek007
(5, 2, 1, '2021-07-08T17:59:19', 'TEXT', 'Nullam imperdiet lacinia tellus nec consectetur. Nullam lobortis ex id diam egestas, nec convallis eros scelerisque. Pellentesque vel eros porta, ultricies turpis quis, condimentum mauris.!'),
(6, 2, 3, '2021-07-08T18:00:41', 'TEXT', 'Fusce et sodales ante, at congue arcu. Aenean eget felis consequat, ornare ipsum non, gravida nisi. Praesent in nunc dolor. Nullam sit amet nisl ornare turpis laoreet vehicula. Maecenas vulputate dapibus dolor, ut dignissim arcu scelerisque aliquam. Aenean non semper mi.'),
(7, 2, 3, '2021-07-08T18:01:13', 'TEXT', 'Mauris rutrum convallis erat, ac pellentesque urna porttitor sollicitudin. Vestibulum odio ex, accumsan sit amet nisi et, scelerisque rhoncus dui.'),
(8, 2, 1, '2021-07-08T18:03:59', 'TEXT', 'Pellentesque eleifend accumsan massa ut elementum. Mauris nibh turpis, sollicitudin vel scelerisque nec, tincidunt id turpis. Quisque ac massa mattis'),
(9, 2, 1, '2021-07-16T23:59:20', 'TEXT', 'Scelerisque nisi sed, tincidunt risus. Suspendisse viverra aliquam dolor vitae euismod. Ut vehicula lectus sed mi varius venenatis. Cras auctor, diam tempor pretium mattis, nulla ante suscipit lacus, fermentum vulputate purus dui et justo'),
(10, 2, 3, '2021-07-17T00:01:37', 'TEXT', 'Mauris sem dui, hendrerit sed libero eu, iaculis maximus quam'),
(11, 2, 3, '2021-07-17T00:03:43', 'TEXT', 'Nam porta quis urna vitae venenatis. Duis venenatis metus sed elementum euismod'),
(12, 2, 3, '2021-07-17T00:04:03', 'TEXT', 'Vestibulum nec orci nulla. Pellentesque vel pharetra tellus, sed mattis diam. Donec viverra felis arcu, quis auctor turpis interdum in'),
(13, 2, 1, '2021-07-17T00:05:17', 'TEXT', 'Mauris commodo rutrum urna ut lacinia. Etiam semper mattis ullamcorper. Nam ut laoreet leo.'),

(14, 3, 4, '2021-06-28T13:03:10', 'TEXT', 'Vivamus eu arcu id diam feugiat egestas non egestas est. Praesent ac tellus nec ante aliquam pellentesque'),  -- grace01 to mrsmith
(15, 3, 3, '2021-06-28T13:03:26', 'TEXT', 'Praesent eget dictum nunc. Ut sodales tortor ac tortor semper, et congue est maximus. Fusce condimentum sagittis massa, in sagittis nibh venenatis nec'),
(16, 3, 3, '2021-06-28T13:03:28', 'TEXT', 'Duis gravida, elit sed efficitur luctus, tellus risus venenatis odio, sit amet egestas sapien neque eu felis'),
(17, 3, 4, '2021-06-28T13:03:30', 'TEXT', 'Curabitur accumsan leo a maximus congue'),
(18, 3, 4, '2021-06-29T18:56:19', 'TEXT', 'Integer pretium finibus elit vel tristique. Vivamus eget dui risus. Ut fringilla eleifend porttitor.'),
(19, 3, 3, '2021-06-29T19:03:56', 'TEXT', 'Nunc vehicula consequat mi, vel eleifend tellus tincidunt ut. Nunc blandit volutpat dolor, sed auctor augue tincidunt eu. Donec pellentesque dignissim nisi eget pretium'),

(20, 4, 4, '2021-06-30T08:00:11', 'TEXT', 'Nam nec ornare diam, vitae scelerisque elit. Curabitur consequat aliquam ipsum vitae porta'),  -- grace01 to janek007
(21, 4, 1, '2021-06-30T08:03:10', 'TEXT', 'Nullam efficitur velit consequat, gravida purus vitae, ultrices odio.'),
(22, 4, 4, '2021-06-30T08:04:49', 'TEXT', 'Vestibulum non maximus nisl. Donec sollicitudin placerat ipsum consectetur facilisis. Quisque vulputate maximus congue'),
(23, 4, 1, '2021-06-30T08:06:02', 'TEXT', 'Etiam ullamcorper nulla in odio euismod mattis'),
(24, 4, 4, '2021-06-30T08:07:07', 'TEXT', 'Curabitur at quam elit'),
(25, 4, 4, '2021-07-06T11:53:33', 'TEXT', 'Pellentesque leo turpis, tempus et blandit ut, gravida sit amet quam. Pellentesque consectetur, felis vel ornare bibendum, dui leo ornare justo, in sagittis risus ex a dui');
