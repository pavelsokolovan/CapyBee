"""Stage a game command key for first deployment; preserve existing signing keys."""
import json
import secrets
import subprocess


def ensure_key():
    result = subprocess.run(
        ["flyctl", "secrets", "list", "--app", "capybee", "--json"],
        check=True, capture_output=True, text=True,
    )
    names = {str(row.get("Name", row.get("name", ""))) for row in json.loads(result.stdout)}
    if names & {"CAPYBEE_GAME_COMMAND_HMAC_KEY", "APP_SECRET"}:
        print("Existing game signing secret preserved.")
        return
    # Pipe directly to Fly; never expose the generated value in arguments or logs.
    key = secrets.token_urlsafe(48)
    result = subprocess.run(
        ["flyctl", "secrets", "import", "--stage", "--app", "capybee"],
        input=f"CAPYBEE_GAME_COMMAND_HMAC_KEY={key}\n",
        capture_output=True, text=True,
    )
    if result.returncode:
        raise RuntimeError("Could not stage the game signing secret; deployment stopped.")
    print("Initial game signing secret staged for deployment.")


if __name__ == "__main__":
    ensure_key()
