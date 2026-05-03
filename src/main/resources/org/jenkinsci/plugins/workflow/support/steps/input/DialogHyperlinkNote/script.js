(function () {
  const DIALOG_CLOSE_ICON = `
  <svg xmlns="http://www.w3.org/2000/svg" class="ionicon" viewBox="0 0 512 512">
    <path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"
          stroke-width="32" d="M368 368L144 144M368 144L144 368"/>
  </svg>`;

  let lastInputDialogTrigger = null;

  document.addEventListener(
    "click",
    (event) => {
      const trigger = event.target.closest?.(".input-step-dialog-opener");
      if (!trigger || trigger.disabled) {
        return;
      }
      event.preventDefault();
      const url = trigger.dataset?.dialogUrl;
      if (!url) {
        return;
      }
      // Disable the trigger while the dialog is fetching / open so a double-click can't
      // call showModal() twice (which throws once another modal is already open).
      trigger.disabled = true;
      lastInputDialogTrigger = trigger;
      openInputStepDialog(url, () => {
        trigger.disabled = false;
      });
    },
    true,
  );

  function openInputStepDialog(url, onAbort) {
    fetch(url, {headers: {Accept: "text/html"}})
      .then((response) => {
        if (!response.ok) {
          console.error("input dialog fetch failed", response);
          if (response.status === 404) {
            notificationBar.show('Error: Input has already been submitted', notificationBar.ERROR)
          } else {
            notificationBar.show('An error occurred when fetching the dialog: ' + response.status, notificationBar.ERROR)
          }
          onAbort();
          return;
        }
        const titleText = response.headers.get("X-Dialog-Title");
        return response.text().then((html) => {
          mountInputStepDialog(html, url, titleText, onAbort);
        });
      })
      .catch((err) => {
        console.error(err);
        onAbort();
      });
  }

  function mountInputStepDialog(html, dialogUrl, titleText, onClose) {
    const dialog = document.createElement("dialog");
    dialog.className = "jenkins-dialog";
    dialog.append(buildTitleBar(dialog, titleText), buildContents(html, dialogUrl));
    document.body.appendChild(dialog);

    // innerHTML doesn't execute <script> tags pulled in by <st:adjunct> (e.g. select.js for the
    // credentials parameter's filling dropdown). Recreate them so the browser loads + runs them,
    // then apply Behaviour once the last external script has loaded.
    recreateScriptsAndApplyBehaviours(dialog);

    const cleanup = () => {
      if (dialog.open) {
        dialog.close();
      }
      dialog.remove();
      if (onClose) {
        onClose();
      }
    };
    dialog.addEventListener("close", cleanup, {once: true});
    dialog.addEventListener("cancel", cleanup, {once: true});
    dialog.addEventListener("click", (event) => {
      if (event.target === dialog) {
        cleanup();
      }
    });

    dialog.showModal();
  }

  function buildTitleBar(dialog, titleText) {
    const titleBar = document.createElement("div");
    titleBar.className = "jenkins-dialog__title";

    const titleSpan = document.createElement("span");
    titleSpan.textContent = titleText ?? "";
    titleBar.appendChild(titleSpan);

    const closeButton = document.createElement("button");
    closeButton.type = "button";
    closeButton.className =
      "jenkins-dialog__title__button jenkins-dialog__title__close-button jenkins-button";
    closeButton.innerHTML = `<span class="jenkins-visually-hidden">Close</span>${DIALOG_CLOSE_ICON}`;
    closeButton.addEventListener("click", () =>
      dialog.dispatchEvent(new Event("cancel")),
    );
    titleBar.appendChild(closeButton);

    return titleBar;
  }

  function buildContents(html, dialogUrl) {
    const body = document.createElement("div");
    body.className = "jenkins-dialog__contents";
    body.innerHTML = html;

    // The form's <f:form action="dialogSubmit"> emits a relative URL. Resolve it against the
    // dialog source URL so the submit posts to /input/{id}/dialogSubmit regardless of which
    // page the user is currently on.
    const form = body.querySelector("form.input-step-dialog-form");
    if (form && dialogUrl) {
      const rawAction = form.getAttribute("action");
      if (rawAction) {
        try {
          const absoluteDialogUrl = new URL(dialogUrl, document.baseURI).href;
          form.action = new URL(rawAction, absoluteDialogUrl).href;
        } catch (err) {
          console.error("failed to resolve dialog form action", err);
        }
      }
    }
    return body;
  }

  document.addEventListener(
    "submit",
    (event) => {
      const form = event.target.closest?.("form.input-step-dialog-form");
      if (!form) {
        return;
      }
      event.preventDefault();
      const action = event.submitter?.name === "abort" ? "abort" : "proceed";

      setHiddenField(form, "inputAction", action);
      if (!buildFormTree(form)) {
        return;
      }

      const buttons = form.querySelectorAll(
        "button[name='proceed'], button[name='abort'], input[type='submit']",
      );
      const setDisabled = (disabled) =>
        buttons.forEach((b) => (b.disabled = disabled));
      setDisabled(true);

      fetch(form.action, {
        method: "POST",
        body: new FormData(form),
        headers: crumb.wrap({}),
      })
        .then((response) => {
          if (!response.ok) {
            console.error("input-step dialog submit failed", response);
            if (response.status === 404) {
              notificationBar.show('Error: Input has already been submitted', notificationBar.ERROR)
              setDisabled(false);
              // fall-through here so that if the input has already been submitted, the dialog is closed
              // no point keeping it open
            } else if (response.status === 400) {
              response.json()
                .then((json) => {
                  notificationBar.show(json.message, notificationBar.ERROR)
                  setDisabled(false);
                })
            } else {
              notificationBar.show('Error input-step dialog submit failed: ' + response.status, notificationBar.ERROR)
              setDisabled(false);
              return
            }
          }
          form.closest("dialog")?.dispatchEvent(new Event("cancel"));
          if (response.ok) {
            replaceTriggerWithLabel();
          }
        })
        .catch((err) => {
          console.error(err);
          notificationBar.show('Error: ' + err, notificationBar.ERROR)
          setDisabled(false);
        });
    },
    true,
  );

  function setHiddenField(form, name, value) {
    let field = form.elements.namedItem(name);
    if (!field) {
      field = document.createElement("input");
      field.type = "hidden";
      field.name = name;
      form.appendChild(field);
    }
    field.value = value;
  }

  function recreateScriptsAndApplyBehaviours(root) {
    const scripts = Array.from(root.getElementsByTagName("script"));
    if (scripts.length === 0) {
      Behaviour.applySubtree(root, true);
      return;
    }
    const apply = () => Behaviour.applySubtree(root, true);
    let pending = scripts.filter((s) => s.src).length;
    scripts.forEach(original => {
      const replacement = document.createElement("script");
      for (const attr of original.attributes) {
        replacement.setAttribute(attr.name, attr.value);
      }
      if (original.text) {
        replacement.text = original.text;
      }
      if (replacement.src) {
        const onDone = () => {
          if (--pending === 0) {
            apply();
          }
        };
        replacement.addEventListener("load", onDone);
        replacement.addEventListener("error", onDone);
      }
      original.parentNode.replaceChild(replacement, original);
    });
    if (pending === 0) {
      apply();
    }
  }

  function replaceTriggerWithLabel() {
    if (!lastInputDialogTrigger) {
      return;
    }
    const replacement = document.createElement("span");
    replacement.textContent =
      lastInputDialogTrigger.dataset?.inputProvidedLabel ?? "";
    lastInputDialogTrigger.replaceWith(replacement);
    lastInputDialogTrigger = null;
  }
})();
