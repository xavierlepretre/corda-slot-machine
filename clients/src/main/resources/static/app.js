"use strict";

// This is a bare-bones version of the logic found in slots.js

$(window).on("load", function () {
  function setMessage(message) {
    $("#message").html("Result: " + message);
  }

  function makeRequest(params, url, dataType = "text") {
    // https://api.jquery.com/jQuery.ajax/
    $.ajax({
      url: url,
      type: params ? "POST" : "GET",
      data: params,
      dataType: "text",
      timeout: 10000,
      success: function (data) {
        setMessage(data);
        return false;
      },
      error: function () {
        setMessage("error!");
        return false;
      },
    });
  }

  $("#test").click(function () {
    makeRequest(null, "/test");
  });
  $("#echo").click(function () {
    makeRequest({ payload: "OK" }, "/echo");
  });
  $("#create").click(function () {
    makeRequest({ name: "anyname" }, "/create");
  });
  $("#spin").click(function () {
    makeRequest({ name: "anyname" }, "/spin");
  });
  $("#spin2").click(function () {
    makeRequest({ name: "anyname" }, "/spin2", "json");
  });
  $("#reels").click(function () {
    function getReels(payout) {
      try {
        const result = { payout_credits: payout };
        return window.ioServer.getResultReels(result).reels;
      } catch (err) {
        return err;
      }
    }
    const message = "200 50 20 15 15 15 15 15 13 12 10 4 4 4 4 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
      .split(" ")
      .map((payout) => `${payout}: ${getReels(payout)}`)
      .join("\n");
    setMessage(message);
  });
});
