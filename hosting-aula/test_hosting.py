"""Pruebas unitarias: no requieren root, no modifican el sistema."""
import unittest
from unittest.mock import patch
import hosting as h


class DatabaseTests(unittest.TestCase):
    def test_default_names(self):
        self.assertEqual(h.default_dbs("ana"), ["db_ana", "db_ana1", "db_ana3"])

    def test_collision_new_users(self):
        with self.assertRaises(h.Error):
            h.validate_db_plan({"alumnos": {}}, ["ana", "ana1"])

    def test_collision_registered_extra(self):
        state = {"alumnos": {"ana": {"mysql_dbs": ["db_ana", "db_ana1", "db_ana3", "db_ana2"]}}}
        with self.assertRaises(h.Error):
            h.validate_db_plan(state, ["ana2"])

    def test_legacy_database(self):
        self.assertEqual(h.owned_dbs({}, "ana"), ["ana"])
        owners = h.validate_db_plan({"alumnos": {"ana": {}}}, ["pedro"])
        self.assertEqual(owners["ana"], "ana")
        self.assertEqual(owners["db_ana1"], "ana")
        self.assertEqual(owners["db_pedro3"], "pedro")

    def test_literal_database_scope(self):
        self.assertEqual(h.db_scope("db_ana", False), chr(96) + "db\\_ana" + chr(96))
        self.assertEqual(h.db_scope("db_ana", True), chr(96) + "db_ana" + chr(96))
        for name in ("db_%", "db_ana; DROP", "db_ana"+chr(96)):
            with self.subTest(name=name), self.assertRaises(h.Error):
                h.db_ident(name)

    def test_grants_both_connections(self):
        sql = h.grants_sql("ana", "db_ana", partial=False)
        self.assertEqual(sql.count("GRANT ALL PRIVILEGES"), 2)
        self.assertIn("db\\_ana", sql)
        self.assertIn("'ana'@'localhost'", sql)
        self.assertIn("'ana'@'127.0.0.1'", sql)
        self.assertNotIn("GRANT OPTION", sql)
        self.assertIn(" FROM ", h.grants_sql("ana", "db_ana", verb="REVOKE"))

    def test_password_sql_literal(self):
        self.assertEqual(h.sql_string("a'b\\c"), "'a''b\\c'")

    def test_refuse_adopt_existing_db(self):
        state = {"alumnos": {"ana": {"activo": True, "mysql_dbs": h.default_dbs("ana")}}}
        with patch.object(h, "db_exists", return_value=True), patch.object(h, "mysql") as mysql:
            with self.assertRaises(h.Error):
                h.extra_db(state, "ana", "2")
            mysql.assert_not_called()

    def test_create_extra(self):
        state = {"alumnos": {"ana": {"activo": True, "mysql_dbs": h.default_dbs("ana")}}}
        with patch.object(h, "db_exists", return_value=False), patch.object(h, "partial_revokes", return_value=False), patch.object(h, "mysql") as mysql, patch.object(h, "save"):
            h.extra_db(state, "ana", "2")
            self.assertIn("db_ana2", state["alumnos"]["ana"]["mysql_dbs"])
            self.assertEqual(mysql.call_count, 2)

    def test_protect_initial_database(self):
        state = {"alumnos": {"ana": {"activo": True, "mysql_dbs": h.default_dbs("ana")}}}
        with self.assertRaises(h.Error):
            h.drop_mysql_db(state, "ana", "db_ana3")

    def test_password_change_does_not_store_secret(self):
        state = {"alumnos": {"ana": {"activo": True}}}
        with patch.object(h, "ask_password", return_value="Example123!"), patch.object(h.sp, "run") as run, patch.object(h, "save"):
            run.return_value.returncode = 0
            h.change_password(state, "ana", "mysql")
            args, kw = run.call_args
            self.assertNotIn("Example123!", args[0])
            self.assertEqual(kw["input"].count("IDENTIFIED BY"), 2)
            self.assertNotIn("Example123!", str(state))
            self.assertTrue(state["alumnos"]["ana"]["password_changed"]["mysql"])

    def test_cli(self):
        p = h.help_parser()
        self.assertEqual(p.parse_args(["db-alta", "ana", "2"]).sufijo, "2")
        self.assertEqual(p.parse_args(["clave", "ana", "mysql"]).servicio, "mysql")
        self.assertTrue(p.parse_args(["clave", "ana", "mysql", "--restablecer"]).restablecer)


if __name__ == "__main__":
    unittest.main()
