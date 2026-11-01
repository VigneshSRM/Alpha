/*
 * The module picker.
 *
 * The cards are laid out normally, then each one is pushed back to the point in
 * the middle of the grid and released, so they appear to burst outwards from a
 * centre point. Nothing here is needed for the form to work: if the script
 * never runs, the cards simply sit still.
 */
(function () {
  "use strict";

  var form = document.getElementById("modulesForm");
  var grid = document.querySelector("[data-module-grid]");
  if (!form || !grid) return;

  var cards = Array.prototype.slice.call(grid.querySelectorAll(".module"));
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  function measure() {
    var box = grid.getBoundingClientRect();
    var cx = box.left + box.width / 2;
    var cy = box.top + box.height / 2;

    cards.forEach(function (card, index) {
      var rect = card.getBoundingClientRect();
      var dx = Math.round(cx - (rect.left + rect.width / 2));
      var dy = Math.round(cy - (rect.top + rect.height / 2));
      card.style.setProperty("--dx", dx + "px");
      card.style.setProperty("--dy", dy + "px");
      card.style.setProperty("--i", String(index));
    });
  }

  function split() {
    if (reduced) {
      grid.setAttribute("data-split", "instant");
      return;
    }
    /* Cards are only hidden once the script is here to bring them back. */
    grid.setAttribute("data-split", "pending");
    measure();
    /* One frame later, so the browser has painted the tucked-in position. */
    window.requestAnimationFrame(function () {
      window.requestAnimationFrame(function () {
        grid.setAttribute("data-split", "1");
      });
    });
  }

  /* The count next to the button is the only live feedback the page needs. */
  var counter = document.querySelector("[data-chosen-count]");
  var total = document.querySelector("[data-total-count]");
  var checkboxes = Array.prototype.slice.call(
    form.querySelectorAll('input[name="modules"]')
  );

  function count() {
    var chosen = checkboxes.filter(function (box) { return box.checked; }).length;
    if (counter) counter.textContent = String(chosen);
    if (total) total.textContent = String(checkboxes.length);
  }

  checkboxes.forEach(function (box) {
    box.addEventListener("change", count);
  });
  count();

  split();
})();
