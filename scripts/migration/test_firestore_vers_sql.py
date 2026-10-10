import unittest

import export_firestore
from firestore_vers_sql import convertir, convertir_episode, convertir_media, generer_sql

ID_A = "11111111-1111-4111-8111-111111111111"
ID_B = "22222222-2222-4222-8222-222222222222"


def doc(**champs):
    base = {"title": "Fallout", "type": "SERIE", "status": "EN_COURS"}
    base.update(champs)
    return base


class ConvertirEpisodeTest(unittest.TestCase):
    def test_saison_et_episode_sont_lus_separement(self):
        self.assertEqual((2, 5), convertir_episode("2:5"))

    def test_un_numero_au_dela_de_mille_reste_exact(self):
        self.assertEqual((1, 1123), convertir_episode("1:1123"))
        self.assertEqual((12, 1000), convertir_episode("12:1000"))

    def test_les_formes_illisibles_sont_refusees(self):
        for brut in ["", "3", "1:2:3", "a:1", "1:", ":1", "-1:2", "1.5:2", "１:２", 12, None, "1:100000", "10000:1"]:
            with self.subTest(brut=brut):
                self.assertIsNone(convertir_episode(brut))


class ConvertirMediaTest(unittest.TestCase):
    def test_document_complet(self):
        media, erreur = convertir_media(ID_A, doc(
            tmdbId=106379, releaseYear=2024, posterUrl="https://image.tmdb.org/a.jpg",
            jellyfinId="abc", rating=4, status="VU", watchedAt=1_700_000_000_000,
        ))
        self.assertIsNone(erreur)
        self.assertEqual("VU", media["status"])
        self.assertEqual(106379, media["tmdb_id"])
        self.assertEqual(1_700_000_000_000, media["watched_at"])

    def test_champs_facultatifs_absents_ou_nuls(self):
        media, erreur = convertir_media(ID_A, doc(rating=None))
        self.assertIsNone(erreur)
        self.assertIsNone(media["rating"])
        self.assertIsNone(media["tmdb_id"])

    def test_les_nombres_firestore_en_flottant_entier_sont_acceptes(self):
        media, erreur = convertir_media(ID_A, doc(tmdbId=12.0))
        self.assertIsNone(erreur)
        self.assertEqual(12, media["tmdb_id"])

    def test_affiche_non_https_abandonnee_comme_dans_l_application(self):
        media, _ = convertir_media(ID_A, doc(posterUrl="http://exemple.fr/a.jpg"))
        self.assertIsNone(media["poster_url"])

    def test_documents_invalides(self):
        cas = {
            "identifiant": (("pas-un-uuid", doc())),
            "titre absent": (ID_A, {"type": "FILM", "status": "VU"}),
            "titre vide": (ID_A, doc(title="")),
            "titre trop long": (ID_A, doc(title="x" * 301)),
            "titre non texte": (ID_A, doc(title=12)),
            "type inconnu": (ID_A, doc(type="JEU")),
            "statut inconnu": (ID_A, doc(status="ABANDONNE")),
            "note hors bornes": (ID_A, doc(rating=6)),
            "note non entière": (ID_A, doc(rating=2.5)),
            "note booléenne": (ID_A, doc(rating=True)),
            "année hors bornes": (ID_A, doc(releaseYear=1800)),
            "tmdb négatif": (ID_A, doc(tmdbId=-1)),
            "watchedAt trop grand": (ID_A, doc(watchedAt=32_503_680_000_001)),
            "caractère nul": (ID_A, doc(title="a\x00b")),
        }
        for nom, (identifiant, donnees) in cas.items():
            with self.subTest(nom):
                media, erreur = convertir_media(identifiant, donnees)
                self.assertIsNone(media)
                self.assertIsNotNone(erreur)


class ConvertirTest(unittest.TestCase):
    def test_episodes_dedoublonnes_tries_et_illisibles_signales(self):
        resultat = convertir([(ID_A, doc(watchedEpisodes=["2:1", "1:2", "1:2", "1:1123", "oups"]))])
        self.assertEqual({ID_A: [(1, 2), (1, 1123), (2, 1)]}, resultat.episodes)
        self.assertEqual(3, resultat.nb_episodes)
        self.assertEqual(1, len(resultat.episodes_ignores))

    def test_champ_absent_ou_liste_vide_ne_donne_aucun_episode(self):
        resultat = convertir([(ID_A, doc()), (ID_B, doc(watchedEpisodes=[]))])
        self.assertEqual(2, len(resultat.medias))
        self.assertEqual({}, resultat.episodes)

    def test_un_document_ecarte_ne_bloque_pas_les_autres(self):
        resultat = convertir([(ID_A, doc(type="JEU")), (ID_B, doc())])
        self.assertEqual([ID_B], [m["id"] for m in resultat.medias])
        self.assertEqual([ID_A], [i for i, _ in resultat.ecartes])

    def test_les_episodes_d_un_document_ecarte_ne_sont_pas_importes(self):
        resultat = convertir([(ID_A, doc(type="JEU", watchedEpisodes=["1:1"]))])
        self.assertEqual({}, resultat.episodes)

    def test_identifiant_en_double_apres_normalisation(self):
        resultat = convertir([(ID_A, doc()), (ID_A.upper(), doc())])
        self.assertEqual(1, len(resultat.medias))
        self.assertEqual(1, len(resultat.ecartes))

    def test_sortie_deterministe(self):
        premier = generer_sql(convertir([(ID_B, doc()), (ID_A, doc())]), "a@exemple.fr")
        second = generer_sql(convertir([(ID_A, doc()), (ID_B, doc())]), "a@exemple.fr")
        self.assertEqual(premier, second)


class GenererSqlTest(unittest.TestCase):
    def test_les_apostrophes_sont_doublees(self):
        sql = generer_sql(convertir([(ID_A, doc(title="L'Attaque des Titans"))]), "o'brien@exemple.fr")
        self.assertIn("'L''Attaque des Titans'::text", sql)
        self.assertIn("lower('o''brien@exemple.fr')", sql)

    def test_transaction_et_rejouable(self):
        sql = generer_sql(convertir([(ID_A, doc(watchedEpisodes=["1:1"]))]), "a@exemple.fr")
        self.assertTrue(sql.lstrip().splitlines()[1].startswith("begin;"))
        self.assertIn("on conflict (id) do nothing", sql)
        self.assertIn("on conflict do nothing", sql)
        self.assertIn("commit;", sql)

    def test_aucune_instruction_destructrice(self):
        sql = generer_sql(convertir([(ID_A, doc(watchedEpisodes=["1:1"]))]), "a@exemple.fr").lower()
        for interdit in ["delete ", "truncate", "drop table public", "update public"]:
            self.assertNotIn(interdit, sql)

    def test_sans_donnee_le_sql_reste_valide(self):
        sql = generer_sql(convertir([]), "a@exemple.fr")
        self.assertIn("array[null]", sql)
        self.assertNotIn("insert into public.media", sql)


class LectureFirestoreTest(unittest.TestCase):
    """La lecture ne passe que par `stream()` : le faux client n'offre aucune méthode d'écriture."""

    def test_lit_uniquement_la_collection_du_compte(self):
        chemins = []

        class Document:
            def __init__(self, id, donnees):
                self.id, self._donnees = id, donnees

            def to_dict(self):
                return self._donnees

        class Collection:
            def __init__(self, chemin):
                self.chemin = chemin

            def document(self, nom):
                return Collection(self.chemin + [nom])

            def collection(self, nom):
                return Collection(self.chemin + [nom])

            def stream(self):
                chemins.append(self.chemin)
                return [Document(ID_A, doc())]

        class Client:
            def collection(self, nom):
                return Collection([nom])

        documents = export_firestore.lire_firestore(Client(), "uid-ami")

        self.assertEqual([["users", "uid-ami", "movies"]], chemins)
        self.assertEqual([(ID_A, doc())], documents)


if __name__ == "__main__":
    unittest.main()
