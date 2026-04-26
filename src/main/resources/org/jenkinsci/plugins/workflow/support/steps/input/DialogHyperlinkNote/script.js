Behaviour.specify(
  "form.input-step-dialog-form",
  "input-step-dialog-form",
  0,
  function (form) {
    function setAction(name) {
      let hidden = form.querySelector("input[name='inputAction']");
      if (!hidden) {
        hidden = document.createElement("input");
        hidden.type = "hidden";
        hidden.name = "inputAction";
        form.appendChild(hidden);
      }
      hidden.value = name;
    }
    form.addEventListener("click", function (event) {
      const button = event.target.closest("button[name='proceed'], button[name='abort']");
      if (button) {
        setAction(button.name);
      }
    });
    form.addEventListener(
      "submit",
      function (event) {
        const submitter = event.submitter;
        if (submitter && (submitter.name === "proceed" || submitter.name === "abort")) {
          setAction(submitter.name);
        }
      },
      true,
    );
  },
);

// Remember which "Input requested" button was used to open the most recent dialog so we can
// update its appearance after the input is settled.
let lastInputDialogTrigger = null;
document.addEventListener(
  "click",
  function (event) {
    var trigger = event.target.closest(".input-step-dialog-opener");
    if (trigger) {
      lastInputDialogTrigger = trigger;
    }
  },
  true,
);

Behaviour.specify(
  ".input-step-dialog-completed",
  "input-step-close-dialog",
  0,
  function (marker) {
    const dialog = document.querySelector("dialog.jenkins-dialog[open]");
    if (dialog) {
      dialog.dispatchEvent(new Event("cancel"));
    }
    if (lastInputDialogTrigger) {
      // The input is settled — turn the trigger button into a static, non-interactive label so
      // the user can see the input has been handled and cannot reopen the dialog.
      lastInputDialogTrigger.textContent = "Input provided";
      lastInputDialogTrigger.disabled = true;
      lastInputDialogTrigger.classList.remove(
        "jenkins-button--primary",
        "input-step-dialog-opener",
      );
      lastInputDialogTrigger.removeAttribute("data-type");
      lastInputDialogTrigger.removeAttribute("data-dialog-url");
      lastInputDialogTrigger = null;
    }
  },
);
