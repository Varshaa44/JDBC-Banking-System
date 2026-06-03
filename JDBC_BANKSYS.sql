CREATE DATABASE JDBC_BANK_SYSTEM;
USE JDBC_BANK_SYSTEM;

-- CUSTOMER TABLE
CREATE TABLE customer (
    cus_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    encrypted_pwd VARCHAR(255) NOT NULL,
    txn_count INT DEFAULT 0 NOT NULL,
    must_change_pwd BOOLEAN DEFAULT FALSE NOT NULL
);

-- ACCOUNT TABLE
CREATE TABLE account (
    acc_no INT AUTO_INCREMENT PRIMARY KEY,
    cus_id INT NOT NULL,
    balance FLOAT DEFAULT 10000 NOT NULL,
    FOREIGN KEY (cus_id) REFERENCES customer(cus_id)	
) AUTO_INCREMENT = 1001;

CREATE TABLE transactiontype (
    type VARCHAR(20) PRIMARY KEY
);

INSERT INTO transactiontype VALUES
('OPENING'), ('DEPOSIT'), ('WITHDRAWAL'),
('TRANSFER_IN'), ('TRANSFER_OUT'),
('OPERATION_FEE'), ('MAINTENANCE_FEE');

-- TRANSACTION TABLE
CREATE TABLE transaction (
    txn_id INT AUTO_INCREMENT PRIMARY KEY,
    acc_no INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    amt FLOAT NOT NULL,
    balance_after FLOAT NOT NULL,
    note VARCHAR(100) DEFAULT '',
    FOREIGN KEY (acc_no) REFERENCES account(acc_no),
    FOREIGN KEY (type) REFERENCES transactiontype(type)
);

-- PASSWORD HISTORY TABLE
CREATE TABLE pwdhistory (
    id INT AUTO_INCREMENT PRIMARY KEY,
    cus_id INT NOT NULL,
    encrypt_pwd VARCHAR(255) NOT NULL,
    slot INT NOT NULL, -- track the 3 passwd history from Java
    FOREIGN KEY (cus_id) REFERENCES customer(cus_id)
);

CREATE TABLE login_log (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    cus_id INT NOT NULL,
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(10), -- Will store 'SUCCESS' or 'FAILED'
    FOREIGN KEY (cus_id) REFERENCES customer(cus_id)
);

-- Use these to view tables after execution
select * from customer;
select * from login_log;
select * from transaction;
select * from pwdhistory;
select * from account;
show tables from BANK;

-- Use these to clear out data and start program from scratch
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE login_log;
TRUNCATE TABLE transaction;
TRUNCATE TABLE pwdhistory;
TRUNCATE TABLE account;
TRUNCATE TABLE customer;
SET FOREIGN_KEY_CHECKS = 1;