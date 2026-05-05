from pathlib import Path

p = Path("app/src/main/res/values/strings.xml")
text = p.read_text(encoding="utf-8")
old = """    <string name=\"turn_usage_sheet_hint\">Context window and account rate limits for this chat (from the bridge only).</string>
    <string name=\"turn_usage_sheet_refresh_all\">Refresh</string>"""
new = """    <string name=\"turn_usage_sheet_hint\">Bridge/session status, usage limits for this chat, and locally stored assistant change-set snapshots.</string>
    <string name=\"turn_usage_sheet_runtime_section_title\">Bridge &amp; thread</string>
    <string name=\"turn_usage_sheet_usage_limits_section_title\">Usage &amp; limits</string>
    <string name=\"turn_usage_sheet_runtime_bridge_label\">Bridge</string>
    <string name=\"turn_usage_sheet_runtime_session_label\">Session</string>
    <string name=\"turn_usage_sheet_runtime_turn_label\">Turn</string>
    <string name=\"turn_usage_sheet_bridge_connected\">Connected</string>
    <string name=\"turn_usage_sheet_bridge_connecting\">Connecting…</string>
    <string name=\"turn_usage_sheet_bridge_disconnected\">Disconnected</string>
    <string name=\"turn_usage_sheet_bridge_error\">Error — %1$s</string>
    <string name=\"turn_usage_sheet_session_ready\">Ready</string>
    <string name=\"turn_usage_sheet_session_not_ready\">Not ready</string>
    <string name=\"turn_usage_sheet_turn_running\">Running</string>
    <string name=\"turn_usage_sheet_turn_idle\">Idle</string>
    <string name=\"turn_usage_strip_turn_active\">Turn active</string>
    <string name=\"turn_usage_sheet_assistant_section_title\">Assistant changes (local ledger)</string>
    <string name=\"turn_usage_sheet_assistant_section_hint\">Stored on device from captured patches; applying a revert goes through the bridge when that RPC is wired.</string>
    <string name=\"turn_usage_sheet_assistant_empty\">No assistant change sets recorded for this chat.</string>
    <string name=\"turn_usage_sheet_assistant_change_row\">Turn …%1$s · %2$d files · %3$s</string>
    <string name=\"turn_usage_sheet_revert_latest\">Revert latest assistant patch</string>
    <string name=\"turn_usage_sheet_revert_followup_hint\">Next: call the desktop revert preview/apply RPC from services when available.</string>
    <string name=\"turn_usage_assistant_revert_blocked_runtime\">Revert runs on your bridge/desktop session — Android hook not wired yet.</string>
    <string name=\"turn_usage_assistant_revert_blocked_reverted\">Already reverted.</string>
    <string name=\"turn_usage_assistant_revert_blocked_collecting\">Change set still collecting.</string>
    <string name=\"turn_usage_assistant_revert_blocked_missing_inverse\">Missing inverse patch payload.</string>
    <string name=\"turn_usage_assistant_revert_blocked_not_revertable\">Marked not revertable.</string>
    <string name=\"turn_usage_assistant_revert_blocked_failed\">Last capture failed.</string>
    <string name=\"ai_changeset_status_collecting\">Collecting</string>
    <string name=\"ai_changeset_status_ready\">Ready</string>
    <string name=\"ai_changeset_status_reverted\">Reverted</string>
    <string name=\"ai_changeset_status_failed\">Failed</string>
    <string name=\"ai_changeset_status_not_revertable\">Not revertable</string>
    <string name=\"turn_usage_sheet_refresh_all\">Refresh</string>"""
if old not in text:
    raise SystemExit("anchor not found")
p.write_text(text.replace(old, new), encoding="utf-8")
