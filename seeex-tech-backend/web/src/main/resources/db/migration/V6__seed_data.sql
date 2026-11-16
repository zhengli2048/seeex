insert into company_profile (company_code, company_name) values ('demo', 'Seeex Demo') on duplicate key update company_name = values(company_name);
insert into employee (employee_no, display_name, status) values ('E0001', 'Demo User', 'ACTIVE') on duplicate key update display_name = values(display_name);

