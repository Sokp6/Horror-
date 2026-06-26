using UnityEngine;

/// <summary>First-person player controller: movement, mouse look, interaction.</summary>
[RequireComponent(typeof(CharacterController))]
public class FPSController : MonoBehaviour
{
    [Header("Movement")]
    public float walkSpeed = 5f;
    public float runSpeed = 9f;
    public float crouchSpeed = 2.5f;
    public float jumpForce = 6f;
    public float gravity = -20f;

    [Header("Look")]
    public float mouseSensitivity = 2f;
    public float touchSensitivity = 0.15f;
    public float lookSmoothTime = 0.1f;

    [Header("Bob")]
    public float bobSpeed = 8f;
    public float bobAmount = 0.05f;

    private CharacterController cc;
    private Camera playerCam;
    private Vector3 velocity;
    private float xRotation = 0f;
    private float defaultCamY;
    private bool isGrounded;
    private bool isRunning;
    private bool isCrouching;
    private float standingHeight = 2f;
    private float crouchingHeight = 1f;

    // Mobile input
    private Vector2 moveInput;
    private Vector2 lookInput;
    private bool jumpPressed;
    public bool useMobileInput = false;

    void Start()
    {
        cc = GetComponent<CharacterController>();
        playerCam = GetComponentInChildren<Camera>();
        defaultCamY = playerCam.transform.localPosition.y;

        if (!useMobileInput)
        {
            Cursor.lockState = CursorLockMode.Locked;
            Cursor.visible = false;
        }
    }

    void Update()
    {
        if (GameManager.Instance != null && GameManager.Instance.isGameOver) return;

        HandleLook();
        HandleMovement();
        HandleBob();
    }

    void HandleLook()
    {
        float mx, my;

        if (useMobileInput)
        {
            mx = lookInput.x * touchSensitivity;
            my = lookInput.y * touchSensitivity;
        }
        else
        {
            mx = Input.GetAxis("Mouse X") * mouseSensitivity;
            my = Input.GetAxis("Mouse Y") * mouseSensitivity;
        }

        xRotation -= my;
        xRotation = Mathf.Clamp(xRotation, -90f, 90f);

        playerCam.transform.localRotation = Quaternion.Euler(xRotation, 0f, 0f);
        transform.Rotate(Vector3.up * mx);
    }

    void HandleMovement()
    {
        isGrounded = cc.isGrounded;
        if (isGrounded && velocity.y < 0) velocity.y = -2f;

        // Input
        float h, v;
        if (useMobileInput)
        {
            h = moveInput.x; v = moveInput.y;
        }
        else
        {
            h = Input.GetAxis("Horizontal"); v = Input.GetAxis("Vertical");
            isRunning = Input.GetKey(KeyCode.LeftShift);
            isCrouching = Input.GetKey(KeyCode.C);
            jumpPressed = Input.GetButtonDown("Jump");
        }

        Vector3 move = transform.right * h + transform.forward * v;
        float speed = isCrouching ? crouchSpeed : (isRunning ? runSpeed : walkSpeed);
        cc.Move(move * speed * Time.deltaTime);

        // Jump
        if (jumpPressed && isGrounded)
        {
            velocity.y = Mathf.Sqrt(jumpForce * -2f * gravity);
            jumpPressed = false;
        }

        // Crouch
        float targetHeight = isCrouching ? crouchingHeight : standingHeight;
        cc.height = Mathf.Lerp(cc.height, targetHeight, 10f * Time.deltaTime);

        // Gravity
        velocity.y += gravity * Time.deltaTime;
        cc.Move(velocity * Time.deltaTime);
    }

    void HandleBob()
    {
        float speed = new Vector3(cc.velocity.x, 0, cc.velocity.z).magnitude;
        if (speed > 0.1f && isGrounded)
        {
            float bob = Mathf.Sin(Time.time * bobSpeed) * bobAmount * (speed / walkSpeed);
            playerCam.transform.localPosition = new Vector3(
                playerCam.transform.localPosition.x,
                defaultCamY + bob,
                playerCam.transform.localPosition.z);
        }
    }

    // Called by mobile UI
    public void SetMoveInput(Vector2 input) => moveInput = input;
    public void SetLookInput(Vector2 input) => lookInput = input;
    public void SetJump(bool jump) => jumpPressed = jump;
    public void SetRun(bool run) => isRunning = run;
    public void SetCrouch(bool crouch) => isCrouching = crouch;
}
