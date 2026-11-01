/* Dashboard niceties: live session countdown + local timestamps. */
(function () {
  "use strict";

  function pad(value) { return value < 10 ? "0" + value : String(value); }

  function formatCountdown(seconds) {
    if (seconds <= 0) return "Expired";
    var hours = Math.floor(seconds / 3600);
    var minutes = Math.floor((seconds % 3600) / 60);
    var secs = seconds % 60;
    if (hours > 0) return hours + "h " + pad(minutes) + "m";
    return pad(minutes) + "m " + pad(secs) + "s";
  }

  function init() {
    var timer = document.getElementById("sessionTimer");
    if (timer) {
      var expires = Number(timer.getAttribute("data-expires")) || 0;
      var tick = function () {
        var left = Math.round(expires - Date.now() / 1000);
        timer.textContent = formatCountdown(left);
        if (left <= 0) {
          timer.textContent = "Expired";
          window.clearInterval(handle);
        }
      };
      tick();
      var handle = window.setInterval(tick, 1000);
    }

    Array.prototype.forEach.call(document.querySelectorAll("[data-unixtime]"), function (node) {
      var value = Number(node.getAttribute("data-unixtime")) || 0;
      if (!value) {
        node.textContent = "—";
        return;
      }
      node.textContent = new Date(value * 1000).toLocaleString(undefined, {
        dateStyle: "medium",
        timeStyle: "short"
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
