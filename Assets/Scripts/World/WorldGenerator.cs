using UnityEngine;
using System.Collections.Generic;

/// <summary>Procedurally generates the open world: abandoned town with buildings, forest, graveyard.</summary>
public class WorldGenerator : MonoBehaviour
{
    [Header("World Size")]
    public int worldWidth = 200;
    public int worldHeight = 200;
    public float tileSize = 4f;

    [Header("Buildings")]
    public int buildingCount = 8;
    public GameObject[] buildingPrefabs;
    public GameObject[] wallPrefabs;
    public GameObject floorPrefab;
    public GameObject roadPrefab;
    public GameObject treePrefab;
    public GameObject gravestonePrefab;

    [Header("Spawns")]
    public GameObject[] monsterPrefabs;
    public int monsterCount = 15;
    public GameObject bossPrefab;
    public Vector3 bossSpawnPosition = new Vector3(50, 0, 80);
    public GameObject[] itemPrefabs;
    public int itemCount = 25;

    [Header("Lighting")]
    public GameObject streetLightPrefab;
    public Material darkSkyMaterial;
    public Color fogColor = new Color(0.05f, 0.03f, 0.08f);
    public float fogDensity = 0.03f;

    private List<Building> buildings = new List<Building>();

    struct Building
    {
        public Vector3 position;
        public Vector2Int size;
        public bool hasInterior;
    }

    void Start()
    {
        RenderSettings.fog = true;
        RenderSettings.fogColor = fogColor;
        RenderSettings.fogDensity = fogDensity;

        GenerateWorld();
        SpawnMonsters();
        SpawnItems();
        SpawnBoss();
        SpawnPlayer();
    }

    void GenerateWorld()
    {
        // Ground plane
        GameObject ground = GameObject.CreatePrimitive(PrimitiveType.Plane);
        ground.transform.localScale = new Vector3(worldWidth / 10f, 1, worldHeight / 10f);
        ground.transform.position = Vector3.zero;
        ground.GetComponent<Renderer>().material.color = new Color(0.15f, 0.12f, 0.08f);

        // Generate buildings
        System.Random rng = new System.Random(42);
        for (int i = 0; i < buildingCount; i++)
        {
            var b = new Building();
            b.size = new Vector2Int(rng.Next(3, 8), rng.Next(3, 8));
            b.position = new Vector3(
                rng.Next(10, worldWidth - 10),
                0,
                rng.Next(10, worldHeight - 10));
            b.hasInterior = i < 5; // First 5 buildings have interiors

            // Avoid overlap
            bool tooClose = false;
            foreach (var existing in buildings)
            {
                if (Vector3.Distance(b.position, existing.position) < 25f)
                { tooClose = true; break; }
            }
            if (tooClose) continue;

            buildings.Add(b);
            GenerateBuilding(b);
        }

        // Roads between buildings
        for (int i = 0; i < buildings.Count - 1; i++)
        {
            GenerateRoad(buildings[i].position, buildings[i + 1].position);
        }

        // Forest around edges
        for (int i = 0; i < 200; i++)
        {
            float x = rng.Next(0, worldWidth);
            float z = rng.Next(0, worldHeight);
            // Only place trees away from buildings
            bool nearBuilding = false;
            foreach (var b in buildings)
            {
                if (Vector2.Distance(new Vector2(x, z), new Vector2(b.position.x, b.position.z)) < b.size.x * tileSize + 5f)
                { nearBuilding = true; break; }
            }
            if (!nearBuilding && treePrefab != null)
            {
                Instantiate(treePrefab, new Vector3(x, 0, z), Quaternion.Euler(0, rng.Next(0, 360), 0), transform);
            }
        }

        // Graveyard area
        for (int i = 0; i < 30; i++)
        {
            float gx = 160 + rng.Next(0, 35);
            float gz = 140 + rng.Next(0, 40);
            if (gravestonePrefab != null)
                Instantiate(gravestonePrefab, new Vector3(gx, 0, gz), Quaternion.Euler(0, rng.Next(0, 360), 0), transform);
        }
    }

    void GenerateBuilding(Building b)
    {
        float bx = b.position.x;
        float bz = b.position.z;
        float bw = b.size.x * tileSize;
        float bd = b.size.y * tileSize;

        // Walls
        for (int x = 0; x <= b.size.x; x++)
        {
            PlaceWall(bx + x * tileSize, 0, bz, 0);
            PlaceWall(bx + x * tileSize, 0, bz + bd, 0);
        }
        for (int z = 0; z <= b.size.y; z++)
        {
            PlaceWall(bx, 0, bz + z * tileSize, 90);
            PlaceWall(bx + bw, 0, bz + z * tileSize, 90);
        }

        // Floor
        for (int x = 0; x < b.size.x; x++)
        {
            for (int z = 0; z < b.size.y; z++)
            {
                if (floorPrefab != null)
                    Instantiate(floorPrefab, new Vector3(bx + x * tileSize + tileSize / 2, -0.1f, bz + z * tileSize + tileSize / 2), Quaternion.identity, transform);
            }
        }

        // Door opening (leave gap in one wall)
        int doorWall = Random.Range(0, 4);
        float doorX = bx + b.size.x / 2 * tileSize;
        float doorZ = bz + b.size.y / 2 * tileSize;
    }

    void PlaceWall(float x, float y, float z, float rotY)
    {
        if (wallPrefabs == null || wallPrefabs.Length == 0)
        {
            GameObject wall = GameObject.CreatePrimitive(PrimitiveType.Cube);
            wall.transform.position = new Vector3(x, y + 1.5f, z);
            wall.transform.localScale = new Vector3(tileSize, 3f, 0.3f);
            wall.transform.rotation = Quaternion.Euler(0, rotY, 0);
            wall.transform.SetParent(transform);
            wall.GetComponent<Renderer>().material.color = new Color(0.2f, 0.18f, 0.15f);
        }
        else
        {
            Instantiate(wallPrefabs[Random.Range(0, wallPrefabs.Length)], new Vector3(x, y, z), Quaternion.Euler(0, rotY, 0), transform);
        }
    }

    void GenerateRoad(Vector3 from, Vector3 to)
    {
        float dist = Vector3.Distance(from, to);
        int steps = Mathf.CeilToInt(dist / tileSize);
        for (int i = 0; i <= steps; i++)
        {
            Vector3 pos = Vector3.Lerp(from, to, (float)i / steps);
            if (roadPrefab != null)
                Instantiate(roadPrefab, new Vector3(pos.x, 0.01f, pos.z), Quaternion.identity, transform);
        }
    }

    void SpawnMonsters()
    {
        if (monsterPrefabs == null) return;
        System.Random rng = new System.Random(1337);
        for (int i = 0; i < monsterCount; i++)
        {
            Vector3 pos = new Vector3(rng.Next(5, worldWidth - 5), 0, rng.Next(5, worldHeight - 5));
            int idx = rng.Next(0, monsterPrefabs.Length);
            Instantiate(monsterPrefabs[idx], pos, Quaternion.identity, transform);
        }
    }

    void SpawnItems()
    {
        if (itemPrefabs == null) return;
        System.Random rng = new System.Random(777);
        for (int i = 0; i < itemCount; i++)
        {
            // Place items near buildings
            if (buildings.Count > 0)
            {
                var b = buildings[rng.Next(0, buildings.Count)];
                Vector3 pos = b.position + new Vector3(rng.Next(-5, 6), 0.5f, rng.Next(-5, 6));
                int idx = rng.Next(0, itemPrefabs.Length);
                Instantiate(itemPrefabs[idx], pos, Quaternion.identity, transform);
            }
        }
    }

    void SpawnBoss()
    {
        if (bossPrefab != null)
        {
            // Boss in underground bunker area
            Instantiate(bossPrefab, bossSpawnPosition, Quaternion.identity, transform);
        }
    }

    void SpawnPlayer()
    {
        GameObject player = GameObject.FindGameObjectWithTag("Player");
        if (player != null)
        {
            player.transform.position = new Vector3(worldWidth / 2f, 2f, 5f);
        }
    }
}
