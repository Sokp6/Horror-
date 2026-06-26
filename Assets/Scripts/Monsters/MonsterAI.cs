using UnityEngine;
using UnityEngine.AI;

/// <summary>Monster AI: patrol → chase → attack. Horror atmosphere focused.</summary>
[RequireComponent(typeof(NavMeshAgent))]
public class MonsterAI : MonoBehaviour
{
    public enum State { Idle, Patrol, Chase, Attack, Stunned, Dead }
    public enum MonsterType { Shadow, Screamer, Wraith, Abomination }

    [Header("Stats")]
    public MonsterType type = MonsterType.Shadow;
    public float maxHealth = 100f;
    public float health = 100f;
    public float damage = 15f;
    public float attackCooldown = 1.5f;
    public float detectionRange = 20f;
    public float attackRange = 2.5f;
    public float patrolSpeed = 2f;
    public float chaseSpeed = 5f;
    public float sanityDrainPerSecond = 3f;

    [Header("Patrol")]
    public Transform[] patrolPoints;
    public float waitAtPoint = 2f;

    [Header("Effects")]
    public AudioClip idleSound;
    public AudioClip chaseSound;
    public AudioClip attackSound;
    public AudioClip deathSound;
    public GameObject deathEffect;

    private State state = State.Idle;
    private NavMeshAgent agent;
    private Transform player;
    private float attackTimer;
    private int patrolIndex;
    private float waitTimer;
    private float stunTimer;
    private Animator anim;
    private AudioSource audioSource;

    void Start()
    {
        agent = GetComponent<NavMeshAgent>();
        anim = GetComponent<Animator>();
        audioSource = GetComponent<AudioSource>();
        player = Camera.main?.transform;
        health = maxHealth;

        SetupByType();
        state = State.Patrol;
    }

    void SetupByType()
    {
        switch (type)
        {
            case MonsterType.Shadow:
                maxHealth = 60f; damage = 12f; patrolSpeed = 1.5f; chaseSpeed = 4f;
                detectionRange = 18f; sanityDrainPerSecond = 2f;
                break;
            case MonsterType.Screamer:
                maxHealth = 30f; damage = 25f; patrolSpeed = 2f; chaseSpeed = 7f;
                detectionRange = 25f; attackCooldown = 3f; sanityDrainPerSecond = 5f;
                break;
            case MonsterType.Wraith:
                maxHealth = 50f; damage = 18f; patrolSpeed = 2.5f; chaseSpeed = 5.5f;
                detectionRange = 22f; sanityDrainPerSecond = 4f;
                break;
            case MonsterType.Abomination:
                maxHealth = 500f; damage = 35f; patrolSpeed = 1f; chaseSpeed = 3.5f;
                detectionRange = 30f; attackRange = 4f; sanityDrainPerSecond = 8f;
                break;
        }
        health = maxHealth;
    }

    void Update()
    {
        if (state == State.Dead) return;
        if (GameManager.Instance != null && GameManager.Instance.isGameOver)
        {
            agent.isStopped = true; return;
        }

        if (stunTimer > 0) { stunTimer -= Time.deltaTime; return; }
        if (attackTimer > 0) attackTimer -= Time.deltaTime;

        float dist = player != null ? Vector3.Distance(transform.position, player.position) : 999f;

        switch (state)
        {
            case State.Idle:
                state = State.Patrol; break;

            case State.Patrol:
                Patrol(dist);
                break;

            case State.Chase:
                Chase(dist);
                break;

            case State.Attack:
                Attack(dist);
                break;

            case State.Stunned:
                if (stunTimer <= 0) state = State.Chase;
                break;
        }

        // Sanity drain when player is nearby
        if (dist < detectionRange * 0.7f && GameManager.Instance != null)
        {
            GameManager.Instance.DrainSanity(sanityDrainPerSecond * Time.deltaTime);
        }
    }

    void Patrol(float distToPlayer)
    {
        if (distToPlayer < detectionRange)
        {
            state = State.Chase;
            if (chaseSound != null) PlaySound(chaseSound);
            return;
        }

        agent.speed = patrolSpeed;
        agent.isStopped = false;

        if (patrolPoints == null || patrolPoints.Length == 0)
        {
            // Wander randomly
            if (!agent.hasPath || agent.remainingDistance < 1f)
            {
                Vector3 randomDest = transform.position + Random.insideUnitSphere * 15f;
                NavMeshHit hit;
                if (NavMesh.SamplePosition(randomDest, out hit, 15f, NavMesh.AllAreas))
                    agent.SetDestination(hit.position);
            }
            return;
        }

        if (!agent.hasPath || agent.remainingDistance < 1f)
        {
            if (waitTimer > 0) { waitTimer -= Time.deltaTime; return; }
            patrolIndex = (patrolIndex + 1) % patrolPoints.Length;
            agent.SetDestination(patrolPoints[patrolIndex].position);
            waitTimer = waitAtPoint;
        }
    }

    void Chase(float distToPlayer)
    {
        if (distToPlayer > detectionRange * 1.5f)
        {
            state = State.Patrol;
            agent.isStopped = true;
            return;
        }

        if (distToPlayer < attackRange)
        {
            state = State.Attack;
            agent.isStopped = true;
            return;
        }

        agent.speed = chaseSpeed;
        agent.isStopped = false;
        agent.SetDestination(player.position);
    }

    void Attack(float distToPlayer)
    {
        if (distToPlayer > attackRange * 1.3f)
        {
            state = State.Chase;
            return;
        }

        agent.isStopped = true;
        transform.LookAt(new Vector3(player.position.x, transform.position.y, player.position.z));

        if (attackTimer <= 0)
        {
            attackTimer = attackCooldown;
            if (attackSound != null) PlaySound(attackSound);
            GameManager.Instance?.DamagePlayer(damage);

            // Teleport behind player sometimes (Screamer special)
            if (type == MonsterType.Screamer && Random.value < 0.3f)
            {
                Vector3 behind = player.position - player.forward * 3f + Vector3.up * 0.5f;
                NavMeshHit hit;
                if (NavMesh.SamplePosition(behind, out hit, 3f, NavMesh.AllAreas))
                {
                    transform.position = hit.position;
                }
            }
        }
    }

    public void TakeDamage(float amount)
    {
        health -= amount;
        stunTimer = 0.3f;

        if (health <= 0) Die();
        else { state = State.Stunned; stunTimer = 0.5f; }
    }

    void Die()
    {
        state = State.Dead;
        if (deathSound != null) PlaySound(deathSound);
        if (deathEffect != null) Instantiate(deathEffect, transform.position, Quaternion.identity);
        agent.isStopped = true;
        GetComponent<Collider>().enabled = false;
        Destroy(gameObject, 2f);

        // Drop ammo sometimes
        if (Random.value < 0.5f)
        {
            // Drop ammo pickup - handled by GameManager
            GameManager.Instance?.AddAmmo(Random.Range(1, 4), 0);
        }
    }

    void PlaySound(AudioClip clip)
    {
        if (audioSource != null) audioSource.PlayOneShot(clip);
        else AudioSource.PlayClipAtPoint(clip, transform.position, 0.5f);
    }

    void OnDrawGizmosSelected()
    {
        Gizmos.color = state == State.Chase || state == State.Attack ? Color.red : Color.yellow;
        Gizmos.DrawWireSphere(transform.position, detectionRange);
        Gizmos.color = Color.red;
        Gizmos.DrawWireSphere(transform.position, attackRange);
    }
}
