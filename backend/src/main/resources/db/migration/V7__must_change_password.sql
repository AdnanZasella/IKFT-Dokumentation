-- När Dokumenterare-kontot byter innehavare behåller kontot sitt lösenord, men den nya innehavaren måste byta
-- det vid första inloggningen. Flaggan sätts vid innehavarbytet och nollställs när lösenordet byts.

ALTER TABLE user_account ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
