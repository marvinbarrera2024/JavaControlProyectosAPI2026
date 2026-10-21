CREATE TABLE IF NOT EXISTS roles (
    id INT NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS usuarios (
    id INT NOT NULL AUTO_INCREMENT,
    apellido VARCHAR(255) DEFAULT NULL,
    clave VARCHAR(255) DEFAULT NULL,
    login VARCHAR(255) NOT NULL,
    nombre VARCHAR(255) DEFAULT NULL,
    telefono VARCHAR(255) DEFAULT NULL,
    rol_id INT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuarios_login (login),
    KEY idx_usuarios_rol_id (rol_id),
    CONSTRAINT fk_usuarios_rol FOREIGN KEY (rol_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO roles (nombre)
SELECT 'ROLE_ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE roles.nombre = 'ROLE_ADMIN'
);

INSERT INTO roles (nombre)
SELECT 'ROLE_USER'
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE roles.nombre = 'ROLE_USER'
);

-- Administrador inicial: login admin
-- Clave inicial: ipcIpkFE9_OKFr9LSWNp1MYDK68P_NS4
-- Cambia esta clave después del primer acceso.
INSERT INTO usuarios (nombre, apellido, telefono, login, clave, rol_id)
SELECT
    'Administrador',
    'Sistema',
    NULL,
    'admin',
    '$2a$12$1MwDlg0uLd.wg4fW/gVkDu7LkEtR2tnbBUi5FUL8VEj8y5mG7Dycq',
    roles.id
FROM roles
WHERE roles.nombre = 'ROLE_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM usuarios WHERE login = 'admin'
  )
LIMIT 1;
