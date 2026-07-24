drop table if exists public.user_role cascade;

create table public.user_role (
    id uuid not null,
    user_id varchar(64) null,
    role_id varchar(64) null,
    delete_flag bool not null default false,
    auth_user varchar(64) null,
    constraint user_role_pkey primary key (id)
);

insert into public.user_role (id, user_id, role_id, delete_flag, auth_user)
values('defc2d01-fb38-4d31-b006-fd182b25aa33', '9ffec3c4-2342-427c-a0ec-e22e5f2ec732', '2c6a06d8-8e10-49c4-88fe-7d2f05dd073b', false, '{"id":"123"}');

insert into public.user_role (id, user_id, role_id, delete_flag, auth_user) values
('0191c205-0000-7000-8000-000000000001', 'cursor-user-1', 'cursor-role-1', false, '{"id":"cursor-1"}'),
('0191c205-0001-7000-8000-000000000002', 'cursor-user-2', 'cursor-role-2', false, '{"id":"cursor-2"}'),
('0191c205-0002-7000-8000-000000000003', 'cursor-user-3', 'cursor-role-3', false, '{"id":"cursor-3"}'),
('0191c205-0003-7000-8000-000000000004', 'cursor-user-4', 'cursor-role-4', false, '{"id":"cursor-4"}');
