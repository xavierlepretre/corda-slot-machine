(function () {
  // https://stackoverflow.com/questions/105034/how-to-create-guid-uuid
  function uuidv4() {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (
      c
    ) {
      var r = (Math.random() * 16) | 0,
        v = c == "x" ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  const cookieName = "cordaCodeClubSlotMachineAccount";
  const days = 7;
  // see https://www.npmjs.com/package/js-cookie or https://github.com/js-cookie/js-cookie for a description of this API
  function cookieDelete() {
    Cookies.remove(cookieName);
  }
  function cookieCreate(accountName) {
    Cookies.set(cookieName, accountName, { expires: days });
  }
  function cookieRead() {
    return Cookies.get(cookieName);
  }

  function onError(msg) {
    $el.find(".error").text(msg).show();
  }
  function onSuccess(accountName, balance) {
    // set the account name and the balance
    window.accountName = accountName;
    SlotMachinesBalanceChange(balance);
    // create or delete the cookie
    if ($elRemember.prop("checked")) {
      if (accountName === cookieValue) {
        // cookie already exists -- could refresh it now
        // we said it would expire after days, should that be days after it was created, or days after it was last used?
      } else {
        if (started) cookieDelete();
        cookieCreate(accountName);
      }
    } else {
      if (started) cookieDelete();
    }
    $el.dialog("close");
  }

  function onStart() {
    const accountName = uuidv4();
    const fnSuccess = (result) => {
      onSuccess(accountName, result);
    };
    window.ioServer.create(accountName, fnSuccess, onError);
  }
  function onContinue() {
    const accountName = cookieValue;
    const fnSuccess = (result) => {
      onSuccess(accountName, result);
    };
    window.ioServer.balance(accountName, fnSuccess, onError);
  }

  const cookieValue = cookieRead();
  const started = !!cookieValue;
  const $el = $("#loginDialog");
  $el.find(".start").toggle(!started).click(onStart);
  $el.find(".continue").toggle(started).click(onContinue);
  $el.find(".reset").toggle(started).click(onStart);
  const $elRemember = $el.find("#remember").prop("checked", started);
  $el.find(".days").text(days);
  $el.find(".error").hide();
  $el.dialog({
    modal: true,
    width: 840,
  });
})();
